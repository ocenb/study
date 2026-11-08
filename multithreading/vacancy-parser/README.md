# Парсер вакансий Хабр Карьеры

Приложение Spring Boot принимает прямые ссылки на вакансии Хабр Карьеры, обрабатывает их в фоне и сохраняет результаты в H2. Для каждой вакансии API отдаёт название, компанию, зарплату, требования, город и дату размещения.

## Запуск

Откройте этот `pom.xml` в IDE как Maven-проект, выберите JDK 17 или новее и нажмите **Run** у класса `VacancyParserApplication`. Можно также запустить проект из этой папки в терминале.

```powershell
.\mvnw.cmd spring-boot:run
```

На Linux и macOS выполните `sh ./mvnw spring-boot:run`. При первом запуске Maven Wrapper скачает Maven и зависимости. Для парсинга нужен доступ к `career.habr.com`. Сервис слушает `http://localhost:8080`, а файл `data/vacancies.mv.db` создаётся автоматически в рабочей папке приложения.

## Проверка

Выполняйте команды из папки проекта, где лежит `pom.xml`. В командной строке Windows (`cmd`), macOS и Linux используйте `curl`. В PowerShell на Windows замените `curl` на `curl.exe`.

### 1. Отправьте заявки

Готовое тело запроса лежит в [examples/vacancies.json](examples/vacancies.json). В `urls` можно передать от одной до двадцати ссылок.

```json
{
  "urls": [
    "https://career.habr.com/vacancies/1000150058",
    "https://career.habr.com/vacancies/1000169581"
  ]
}
```

```sh
curl -i -H "Content-Type: application/json" --data-binary "@examples/vacancies.json" "http://localhost:8080/post"
```

Ответ `202 Accepted` содержит список заявок. Если пример ушёл в архив, возьмите актуальную ссылку из [списка вакансий Хабр Карьеры](https://career.habr.com/vacancies).

### 2. Проверьте статус

Скопируйте значение `id` из ответа и подставьте его вместо `JOB_ID`.

```sh
curl "http://localhost:8080/answer/JOB_ID"
```

Дождитесь `SUCCEEDED`, повторяя запрос через несколько секунд. При `FAILED` причина указана в поле `error`.

### 3. Получите вакансии

Откройте [список вакансий](http://localhost:8080/answer) в браузере или получите его через `curl` с сортировкой по названию.

```sh
curl "http://localhost:8080/answer?sortBy=title&direction=asc"
```

`sortBy` принимает `publishedAt`, `title` или `city`, а `direction` принимает `asc` или `desc`. По умолчанию новые вакансии идут первыми.

Для отсутствующей зарплаты или города API возвращает `null`.

## Как работает многопоточность

- `@Scheduled` раз в секунду выбирает ожидающие заявки из H2 и передаёт их в собственный `ThreadPoolExecutor` с четырьмя потоками и потокобезопасной `ArrayBlockingQueue`. В журнале разбора видны имена потоков `vacancy-parse-*`.
- `RestTemplate` загружает страницы Хабр Карьеры. Результаты сохраняются в файловой H2.
- Повторная ссылка получает ID уже ожидающей или выполняющейся заявки.
- `GET /answer` читает вакансии из H2 и сортирует их через `parallelStream()`.

Автоматическую проверку очереди, парсинга и REST API можно запустить командой `.\mvnw.cmd test` в PowerShell или `sh ./mvnw test` в Linux и macOS.
