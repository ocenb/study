import re


def validate_car_number(car_id):
    # Список допустимых букв в номерах
    valid_letters = "АВЕКМНОРСТУХ"

    # Регулярное выражение для проверки номера
    pattern = re.compile(rf"^[{valid_letters}]\d{{3}}[{valid_letters}]{{2}}\d{{2,3}}$")

    if pattern.match(car_id):
        # Разделяем номер и регион
        if car_id[-3].isdigit():
            number = car_id[:-3]
            region = car_id[-3:]
        else:
            number = car_id[:-2]
            region = car_id[-2:]

        return f"Номер {number} валиден. Регион: {region}."
    else:
        return "Номер не валиден."


# Примеры использования
car_id_1 = "А222ВС96"
print(validate_car_number(car_id_1))

car_id_2 = "А322ВВ193"
print(validate_car_number(car_id_2))
