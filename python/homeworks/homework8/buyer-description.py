import csv

# открываем csv файл
with open("web_clients_correct.csv", newline="") as csvfile:
    # открываем txt файл
    with open("description.txt", "w", encoding="utf-8") as description_file:
        # создаём reader object
        reader = csv.reader(csvfile, delimiter=",")
        # пропускаем первую строку
        next(reader)

        # проходимся по каждой строке
        for row in reader:
            # записываем данные в переменные
            name, device_type, browser, sex, age, bill, region = (
                row[0],
                row[1],
                row[2],
                row[3],
                row[4],
                row[5],
                row[6],
            )

            # создаём начало строки, добавляем имя и удаляем лишние пробелы
            description = f"Пользователь {name.replace("  ", " ")} "

            # добавляем пол
            if sex == "male":
                description += f"мужского пола, "
            elif sex == "female":
                description += f"женского пола, "

            # добавляем возраст
            if age[-1] == "1":
                description += f"{age} год "
            elif age[-1] in ["2", "3", "4"]:
                description += f"{age} года "
            else:
                description += f"{age} лет "

            # добавляем стоимость покупки и браузер
            description += f"совершил покупку на {bill} у.е. c браузера {browser}. "

            # добавляем устройство
            if device_type == "mobile":
                description += f"Устройство пользователя — мобильный телефон. "
            elif device_type == "tablet":
                description += f"Устройство пользователя — планшет. "
            elif device_type == "desktop":
                description += f"Устройство пользователя — компьютер. "
            elif device_type == "laptop":
                description += f"Устройство пользователя — ноутбук. "

            # добавляем регион
            if region != "-":
                description += f"Регион, из которого совершалась покупка: {region}."
            else:
                description += f"Регион, из которого совершалась покупка неизвестен."

            # записываем строку в txt файл
            description_file.write(f"{description}\n")
