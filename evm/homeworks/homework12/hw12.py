import os
import stat
import platform
import shutil


def get_filesystem_info(path):
    print(f"\n--- Информация о файловой системе для: {path} ---")
    try:
        if platform.system() == "Windows":
            total, used, free = shutil.disk_usage(path)
            print(f"Общий размер диска (байт): {total}")
            print(f"Использовано (байт): {used}")
            print(f"Свободно (байт): {free}")
        else:
            st = os.statvfs(path)
            print(f"Размер блока (байт): {st.f_bsize}")
            print(f"Общее количество блоков: {st.f_blocks}")
            print(f"Свободные блоки: {st.f_bfree}")
            print(f"Общее количество inodes: {st.f_files}")
            print(f"Свободные inodes: {st.f_ffree}")
    except FileNotFoundError:
        print(f"Ошибка: Путь '{path}' не найден.")
    except OSError as e:
        print(f"Ошибка при получении информации о файловой системе для '{path}': {e}")
    except Exception as e:
        print(f"Ошибка: {e}")


def get_file_info(filepath):
    print(f"\n--- Информация о файле: {filepath} ---")
    try:
        st = os.stat(filepath)

        print(f"Inode: {st.st_ino}")
        print(f"Размер (байт): {st.st_size}")

        file_type = "Неизвестный"
        if stat.S_ISREG(st.st_mode):
            file_type = "Обычный файл"
        elif stat.S_ISDIR(st.st_mode):
            file_type = "Каталог"
        elif stat.S_ISLNK(st.st_mode):
            file_type = "Символическая ссылка"
        print(f"Тип файла: {file_type}")

        print("Права доступа:")
        print(
            f"  Владелец: {'r' if st.st_mode & stat.S_IRUSR else '-'}{'w' if st.st_mode & stat.S_IWUSR else '-'}{'x' if st.st_mode & stat.S_IXUSR else '-'}"
        )
        print(
            f"  Группа:   {'r' if st.st_mode & stat.S_IRGRP else '-'}{'w' if st.st_mode & stat.S_IWGRP else '-'}{'x' if st.st_mode & stat.S_IXGRP else '-'}"
        )
        print(
            f"  Другие:   {'r' if st.st_mode & stat.S_IROTH else '-'}{'w' if st.st_mode & stat.S_IWOTH else '-'}{'x' if st.st_mode & stat.S_IXOTH else '-'}"
        )

    except FileNotFoundError:
        print(f"Ошибка: Файл '{filepath}' не найден.")
    except OSError as e:
        print(f"Ошибка при получении информации о файле: {e}")
    except Exception as e:
        print(f"Ошибка: {e}")


if __name__ == "__main__":
    print(f"ОС: {platform.system()} {platform.release()}")

    if platform.system() == "Windows":
        print("\n--- Для Windows ---")
        get_filesystem_info("C:\\")
        file_to_check = "C:\\Windows\\system.ini"
        if not os.path.exists(file_to_check):
            file_to_check = "C:\\Windows\\System32\\calc.exe"
        get_file_info(file_to_check)

    elif platform.system() == "Linux":
        print("\n--- Для Linux ---")
        get_filesystem_info("/")
        get_file_info("/etc/hosts")

    else:
        print("\nОперационная система не поддерживается.")

    print("\n--- Программа завершена ---")
