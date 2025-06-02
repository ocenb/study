#include <algorithm>
#include <cctype>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <sstream>
#include <string>
#include <vector>

class Team;
class DatabaseManager;

void clearInputBuffer() {
  std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
}

int getIntInput(const std::string& prompt) {
  int value;
  while (true) {
    std::cout << prompt;
    std::cin >> value;
    if (std::cin.good()) {
      clearInputBuffer();
      return value;
    } else {
      std::cout << "Некорректный ввод. Пожалуйста, введите целое число.\n";
      std::cin.clear();
      clearInputBuffer();
    }
  }
}

unsigned int getUnsignedIntInput(const std::string& prompt) {
  long long value;  // Используем long long для обнаружения отрицательных чисел
  while (true) {
    std::cout << prompt;
    std::cin >> value;
    if (std::cin.good() && value >= 0) {
      clearInputBuffer();
      return static_cast<unsigned int>(value);
    } else {
      std::cout << "Некорректный ввод. Пожалуйста, введите неотрицательное "
                   "целое число.\n";
      std::cin.clear();
      clearInputBuffer();
    }
  }
}

std::string getStringInput(const std::string& prompt) {
  std::string value;
  while (true) {
    std::cout << prompt;
    std::getline(std::cin, value);
    if (!value.empty()) {
      // Удаление начальных/конечных пробелов
      value.erase(0, value.find_first_not_of(" \t\n\r\f\v"));
      value.erase(value.find_last_not_of(" \t\n\r\f\v") + 1);
      if (!value.empty()) return value;
    }
    std::cout << "Ввод не может быть пустым. Пожалуйста, попробуйте снова.\n";
  }
}

class Team {
 public:
  std::string name;
  unsigned int gamesPlayed;
  unsigned int wins;
  unsigned int draws;
  unsigned int losses;
  unsigned int points;
  unsigned int rank;

  Team(std::string n = "", unsigned int gp = 0, unsigned int w = 0,
       unsigned int d = 0, unsigned int l = 0)
      : name(n), gamesPlayed(gp), wins(w), draws(d), losses(l), rank(0) {
    calculatePoints();
  }

  void calculatePoints() {
    points = (wins * 3) + (draws * 1);
  }  // 3 очка за победу, 1 за ничью

  void recordWin() {
    wins++;
    gamesPlayed++;
    calculatePoints();
  }

  void recordDraw() {
    draws++;
    gamesPlayed++;
    calculatePoints();
  }

  void recordLoss() {
    losses++;
    gamesPlayed++;
    calculatePoints();
  }

  bool operator<(const Team& other) const { return name < other.name; }

  friend std::ostream& operator<<(std::ostream& os, const Team& team) {
    os << std::left << std::setw(5) << team.rank << std::setw(25) << team.name
       << std::setw(10) << team.points << std::setw(8) << team.gamesPlayed
       << std::setw(8) << team.wins << std::setw(8) << team.draws
       << std::setw(8) << team.losses;
    return os;
  }
};

class DatabaseManager {
 private:
  std::vector<Team> teams;
  std::string currentFilename;

  Team* findTeamByNameInternal(const std::string& name) {
    for (auto& team : teams) {
      if (team.name == name) {
        return &team;
      }
    }
    return nullptr;
  }

  // Сортировка по очкам (убыв.), затем по имени (возр.)
  void sortByPointsForRanking() {
    std::sort(teams.begin(), teams.end(), [](const Team& a, const Team& b) {
      if (a.points != b.points) {
        return a.points > b.points;
      }
      return a.name < b.name;
    });
  }

 public:
  DatabaseManager() : currentFilename("") {}

  void loadFromFile(const std::string& filename) {
    std::ifstream inFile(filename);
    if (!inFile) {
      std::cerr << "Ошибка: Не удалось открыть файл " << filename << std::endl;
      currentFilename = "";
      return;
    }

    teams.clear();
    std::string line;
    while (std::getline(inFile, line)) {
      std::stringstream ss(line);
      std::string name_str, gp_str, w_str, d_str, l_str;

      if (std::getline(ss, name_str, ',') && std::getline(ss, gp_str, ',') &&
          std::getline(ss, w_str, ',') && std::getline(ss, d_str, ',') &&
          std::getline(ss, l_str)) {
        try {
          // Обрезка пробелов у имени
          name_str.erase(0, name_str.find_first_not_of(" \t"));
          name_str.erase(name_str.find_last_not_of(" \t") + 1);

          if (name_str.empty()) {
            std::cerr << "Предупреждение: Пропуск записи с пустым названием "
                         "команды.\n";
            continue;
          }

          unsigned int gp = std::stoul(gp_str);
          unsigned int w = std::stoul(w_str);
          unsigned int d = std::stoul(d_str);
          unsigned int l = std::stoul(l_str);

          if (gp != (w + d + l)) {
            std::cerr
                << "Предупреждение: Несоответствие игровых данных для команды "
                << name_str << ". СИ=" << gp << ", В+Н+П=" << (w + d + l)
                << std::endl;
            gp = w + d + l;
          }

          teams.emplace_back(name_str, gp, w, d, l);
        } catch (const std::invalid_argument& ia) {
          std::cerr << "Предупреждение: Неверный формат данных в строке: "
                    << line << ". Пропуск. Ошибка: " << ia.what() << std::endl;
        } catch (const std::out_of_range& oor) {
          std::cerr << "Предупреждение: Данные вне диапазона в строке: " << line
                    << ". Пропуск. Ошибка: " << oor.what() << std::endl;
        }
      } else {
        if (!line.empty())
          std::cerr << "Предупреждение: Некорректная строка: " << line
                    << ". Пропуск." << std::endl;
      }
    }
    inFile.close();
    currentFilename = filename;
    updateRanks();  // Обновление мест после загрузки
    std::cout << "База данных загружена из " << filename << std::endl;
  }

  void saveToFile(const std::string& filename) {
    std::ofstream outFile(filename);
    if (!outFile) {
      std::cerr << "Ошибка: Не удалось открыть файл " << filename
                << " для сохранения." << std::endl;
      return;
    }

    for (const auto& team : teams) {
      outFile << team.name << "," << team.gamesPlayed << "," << team.wins << ","
              << team.draws << "," << team.losses << std::endl;
    }
    outFile.close();
    currentFilename = filename;
    std::cout << "База данных сохранена в " << filename << std::endl;
  }

  void saveToCurrentFile() {
    if (currentFilename.empty()) {
      std::cout << "Файл еще не был загружен или сохранен. Используйте "
                   "'Сохранить как' сначала.\n";
      std::string newFilename = getStringInput(
          "Введите имя файла для сохранения (например, teams.csv): ");
      if (!newFilename.empty()) {
        saveToFile(newFilename);
      }
      return;
    }
    saveToFile(currentFilename);
  }

  void addRecord() {
    std::cout << "\n--- Добавить новую запись о команде ---\n";
    std::string name = getStringInput("Введите название команды: ");

    if (findTeamByNameInternal(name) != nullptr) {
      std::cout << "Ошибка: Команда с таким названием уже существует.\n";
      return;
    }

    unsigned int gp = getUnsignedIntInput("Введите количество сыгранных игр: ");
    unsigned int wins = getUnsignedIntInput("Введите количество выигрышей: ");
    unsigned int draws = getUnsignedIntInput("Введите количество ничьих: ");
    unsigned int losses = getUnsignedIntInput("Введите количество поражений: ");

    if (gp != (wins + draws + losses)) {
      std::cout << "Предупреждение: Сыгранные игры (" << gp
                << ") не совпадают с суммой выигрышей, ничьих и поражений ("
                << (wins + draws + losses) << ").\n";
      char choice;
      std::cout << "Скорректировать количество сыгранных игр по сумме? (y/n): ";
      std::cin >> choice;
      clearInputBuffer();
      if (tolower(choice) == 'y') {
        gp = wins + draws + losses;
      } else {
        std::cout << "Запись не добавлена из-за несоответствия данных.\n";
        return;
      }
    }

    teams.emplace_back(name, gp, wins, draws, losses);
    updateRanks();
    std::cout << "Запись о команде успешно добавлена.\n";
  }

  void deleteRecord() {
    if (teams.empty()) {
      std::cout << "База данных пуста. Нет записей для удаления.\n";
      return;
    }
    std::cout << "\n--- Удалить запись о команде ---\n";
    displayTableInternal(true);  // Показать с номерами

    unsigned int recordNum = getUnsignedIntInput(
        "Введите номер записи для удаления (0 для отмены): ");

    if (recordNum == 0) {
      std::cout << "Удаление отменено.\n";
      return;
    }

    if (recordNum > 0 && recordNum <= teams.size()) {
      std::cout << "Удаление команды: " << teams[recordNum - 1].name
                << std::endl;
      teams.erase(teams.begin() + (recordNum - 1));
      updateRanks();
      std::cout << "Запись успешно удалена.\n";
    } else {
      std::cout << "Неверный номер записи.\n";
    }
  }

  void displayTableInternal(bool showNumbers = false) {
    if (teams.empty()) {
      std::cout << "База данных пуста.\n";
      return;
    }
    std::cout << "\n--- League Table ---\n";
    std::cout << std::left;
    if (showNumbers) std::cout << std::setw(5) << "№";
    std::cout << std::setw(5) << "Rank";
    std::cout << std::setw(25) << "Team Name";
    std::cout << std::setw(10) << "Points";
    std::cout << std::setw(8) << "GP";
    std::cout << std::setw(8) << "W";
    std::cout << std::setw(8) << "D";
    std::cout << std::setw(8) << "L";
    std::cout << std::endl;

    std::cout << std::string(
                     (showNumbers ? 5 : 0) + 5 + 25 + 10 + 8 + 8 + 8 + 8, '-')
              << std::endl;

    unsigned int displayNumber = 1;
    for (const auto& team : teams) {
      if (showNumbers) std::cout << std::setw(5) << displayNumber++;
      std::cout << team << std::endl;
    }
    std::cout << std::endl;
  }

  void displayTable() {
    updateRanks();  // Убедимся, что места актуальны
    displayTableInternal(false);
  }

  void sortByTeamName() {
    std::sort(teams.begin(), teams.end());
    std::cout << "База данных отсортирована по названию команд.\n";
    std::cout << "\n--- Teams Sorted by Name ---\n";
    std::cout << std::left;
    std::cout << std::setw(25) << "Team Name";
    std::cout << std::setw(10) << "Points";
    std::cout << std::setw(8) << "GP";
    std::cout << std::setw(8) << "W";
    std::cout << std::setw(8) << "D";
    std::cout << std::setw(8) << "L";
    std::cout << std::endl;

    std::cout << std::string(25 + 10 + 8 + 8 + 8 + 8, '-') << std::endl;
    for (const auto& team : teams) {
      std::cout << std::left << std::setw(25) << team.name << std::setw(10)
                << team.points << std::setw(8) << team.gamesPlayed
                << std::setw(8) << team.wins << std::setw(8) << team.draws
                << std::setw(8) << team.losses << std::endl;
    }
    std::cout << std::endl;
  }

  void searchByTeamName() {
    std::cout << "\n--- Поиск по названию команды ---\n";
    std::string name = getStringInput("Введите название команды для поиска: ");

    bool found = false;
    updateRanks();
    std::cout << "\n--- Search Results ---\n";
    std::cout << std::left;
    std::cout << std::setw(5) << "Rank";
    std::cout << std::setw(25) << "Team Name";
    std::cout << std::setw(10) << "Points";
    std::cout << std::setw(8) << "GP";
    std::cout << std::setw(8) << "W";
    std::cout << std::setw(8) << "D";
    std::cout << std::setw(8) << "L";
    std::cout << std::endl;

    std::cout << std::string(5 + 25 + 10 + 8 + 8 + 8 + 8, '-') << std::endl;

    for (const auto& team : teams) {
      if (team.name.find(name) != std::string::npos) {
        std::cout << team << std::endl;
        found = true;
      }
    }

    if (!found) {
      std::cout << "Команды, соответствующие \"" << name << "\", не найдены.\n";
    }
    std::cout << std::endl;
  }

  void filterByPointsRange() {
    std::cout << "\n--- Фильтр по диапазону очков ---\n";
    unsigned int minPoints =
        getUnsignedIntInput("Введите минимальное количество очков: ");
    unsigned int maxPoints =
        getUnsignedIntInput("Введите максимальное количество очков: ");

    if (minPoints > maxPoints) {
      std::cout << "Минимальное количество очков не может быть больше "
                   "максимального.\n";
      return;
    }

    updateRanks();  // Убедимся, что места актуальны
    std::cout << "\n--- Filtered Results (Points: " << minPoints << "-"
              << maxPoints << ") ---\n";
    std::cout << std::left;
    std::cout << std::setw(5) << "Rank";
    std::cout << std::setw(25) << "Team Name";
    std::cout << std::setw(10) << "Points";
    std::cout << std::setw(8) << "GP";
    std::cout << std::setw(8) << "W";
    std::cout << std::setw(8) << "D";
    std::cout << std::setw(8) << "L";
    std::cout << std::endl;

    std::cout << std::string(5 + 25 + 10 + 8 + 8 + 8 + 8, '-') << std::endl;

    bool found = false;
    for (const auto& team : teams) {
      if (team.points >= minPoints && team.points <= maxPoints) {
        std::cout << team << std::endl;
        found = true;
      }
    }
    if (!found) {
      std::cout << "Команды в указанном диапазоне очков не найдены.\n";
    }
    std::cout << std::endl;
  }

  void updateRanks() {
    if (teams.empty()) return;

    sortByPointsForRanking();

    for (size_t i = 0; i < teams.size(); ++i) {
      teams[i].rank = i + 1;
    }
  }

  void processTourData() {
    std::cout << "\n--- Обработка данных тура ---\n";
    unsigned int numMatches =
        getUnsignedIntInput("Введите количество матчей в туре: ");

    for (unsigned int i = 0; i < numMatches; ++i) {
      std::cout << "\nМатч " << (i + 1) << ":\n";
      char resultType;
      while (true) {
        std::cout << "Введите тип результата ('V' для победы/поражения, 'D' "
                     "для ничьей): ";
        std::cin >> resultType;
        resultType = toupper(resultType);
        if (resultType == 'V' || resultType == 'D') {
          clearInputBuffer();
          break;
        }
        std::cout << "Неверный тип результата. Используйте 'V' или 'D'.\n";
        clearInputBuffer();
      }

      std::string team1Name = getStringInput(
          "Введите название Команды 1 (или победившей, если 'V'): ");
      std::string team2Name = getStringInput(
          "Введите название Команды 2 (или проигравшей, если 'V'): ");

      Team* team1 = findTeamByNameInternal(team1Name);
      Team* team2 = findTeamByNameInternal(team2Name);

      if (!team1) {
        std::cout << "Ошибка: Команда '" << team1Name
                  << "' не найдена в базе данных. Пропуск матча.\n";
        continue;
      }
      if (!team2) {
        std::cout << "Ошибка: Команда '" << team2Name
                  << "' не найдена в базе данных. Пропуск матча.\n";
        continue;
      }
      if (team1Name == team2Name) {
        std::cout
            << "Ошибка: Команда не может играть сама с собой. Пропуск матча.\n";
        continue;
      }

      if (resultType == 'V') {  // Команда1 победила, Команда2 проиграла
        team1->recordWin();
        team2->recordLoss();
        std::cout << team1Name << " побеждает " << team2Name << ".\n";
      } else {  // Ничья
        team1->recordDraw();
        team2->recordDraw();
        std::cout << team1Name << " играет вничью с " << team2Name << ".\n";
      }
    }
    updateRanks();
    std::cout << "Данные тура успешно обработаны.\n";
  }

  void displayTopThreeTeams() {
    if (teams.size() < 1) {
      std::cout << "В базе данных недостаточно команд для отображения тройки "
                   "лидеров.\n";
      return;
    }

    updateRanks();  // Убедимся, что места актуальны

    std::cout << "\n--- Top Three Teams ---\n";
    std::cout << std::left;
    std::cout << std::setw(5) << "Rank";
    std::cout << std::setw(25) << "Team Name";
    std::cout << std::setw(10) << "Points";
    std::cout << std::setw(8) << "GP";
    std::cout << std::setw(8) << "W";
    std::cout << std::setw(8) << "D";
    std::cout << std::setw(8) << "L";
    std::cout << std::endl;

    std::cout << std::string(5 + 25 + 10 + 8 + 8 + 8 + 8, '-') << std::endl;

    size_t count = 0;
    for (const auto& team : teams) {
      if (count < 3) {
        std::cout << team << std::endl;
        count++;
      }
    }
    if (count == 0) {
      std::cout << "Нет команд для отображения.\n";
    }
    std::cout << std::endl;
  }
};

// Основная программа
void displayMenu() {
  std::cout << "\n===== Меню Базы Данных Спортивных Команд =====\n";
  std::cout << "1.  Загрузить БД из файла\n";
  std::cout << "2.  Просмотр БД в виде таблицы\n";
  std::cout << "3.  Добавить новую запись\n";
  std::cout << "4.  Удалить запись (по номеру)\n";
  std::cout << "5.  Сохранить БД в текущий файл\n";
  std::cout << "6.  Сохранить БД в новый файл (Сохранить как)\n";
  std::cout << "7.  Сортировать БД по названию команды\n";
  std::cout << "8.  Поиск записи по названию команды\n";
  std::cout << "9.  Выборка данных по диапазону очков\n";
  std::cout << "10. Обработать данные тура\n";
  std::cout << "11. Показать три лучшие команды\n";
  std::cout << "0.  Выход\n";
  std::cout << "===============================================\n";
}

int main() {
  setlocale(LC_ALL, ".UTF-8");
  std::system("chcp 65001 > nul");

  DatabaseManager dbManager;
  int choice;

  // Попытка автозагрузки файла по умолчанию, если он существует
  std::ifstream defaultFile("teams.csv");
  if (defaultFile.good()) {
    defaultFile.close();
    dbManager.loadFromFile("teams.csv");
  } else {
    std::cout << "Файл базы данных по умолчанию 'teams.csv' не найден. "
                 "Пожалуйста, загрузите файл или начните с нуля.\n";
  }

  do {
    displayMenu();
    choice = getIntInput("Ваш выбор: ");

    switch (choice) {
      case 1: {
        std::string filename = getStringInput(
            "Введите имя файла для загрузки (например, teams.csv): ");
        dbManager.loadFromFile(filename);
        break;
      }
      case 2:
        dbManager.displayTable();
        break;
      case 3:
        dbManager.addRecord();
        break;
      case 4:
        dbManager.deleteRecord();
        break;
      case 5:
        dbManager.saveToCurrentFile();
        break;
      case 6: {
        std::string filename = getStringInput(
            "Введите имя файла для сохранения (например, new_teams.csv): ");
        dbManager.saveToFile(filename);
        break;
      }
      case 7:
        dbManager.sortByTeamName();
        break;
      case 8:
        dbManager.searchByTeamName();
        break;
      case 9:
        dbManager.filterByPointsRange();
        break;
      case 10:
        dbManager.processTourData();
        break;
      case 11:
        dbManager.displayTopThreeTeams();
        break;
      case 0:
        std::cout << "Выход из программы.\n";
        break;
      default:
        std::cout << "Неверный выбор. Пожалуйста, попробуйте снова.\n";
    }
  } while (choice != 0);

  return 0;
}