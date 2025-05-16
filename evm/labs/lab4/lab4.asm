START:
    ; Загрузка m и n
    MOV C, [M_ADDR] ; m в C
    MOV D, [N_ADDR] ; n в D

    ; Определение максимального из m и n (в D)
    MOV A, C
    SUB A, D
    JC D_IS_MAX
    MOV D, C
D_IS_MAX:
    ; D = max(m, n)

    ; Перебор x от 1 до D (B - текущий x)
    MOV B, 1

CHECK_X:
    ; Если B > D, выйти
    MOV A, B
    SUB A, D
    JC CONTINUE_LOOP
    JZ CONTINUE_LOOP
    JMP END_PROGRAM

CONTINUE_LOOP:
    ; Вычисление x*x (B*B) -> A
    MOV A, 0
    MOV C, B      ; Множитель x (в C)
MUL_BB:
    OR C, C       ; Проверить множитель C на 0. Устанавливает флаг Z.
    JZ BB_DONE
    ADD A, B      ; A = A + B (добавить множимое x)
    DEC C         ; Уменьшить множитель
    JMP MUL_BB
BB_DONE:
    ; A = x*x

    ; Вычисление m*x (C*B) -> B (временно). Сохраняем x.
    MOV [TEMP_X], B ; Сохранить x
    MOV B, 0        ; Аккумулятор для m*x
    MOV D, [TEMP_X] ; Множитель x (в D)
    MOV C, [M_ADDR] ; Множимое m (в C)
MUL_CB:
    OR D, D       ; Проверить множитель D на 0. Устанавливает флаг Z.
    JZ CB_DONE
    ADD B, C
    DEC D
    JMP MUL_CB
CB_DONE:
    ; B = m*x. Восстановить x.
    MOV B, [TEMP_X]

    ; Вычисление x*x - m*x + n = A - B + [N_ADDR] -> A
    SUB A, B
    ADD A, [N_ADDR]

    ; Проверка результата в A на 0
    OR A, A       ; Устанавливает флаг Z если A = 0
    JZ FOUND_ROOT

    ; Перейти к следующему x
    INC B
    JMP CHECK_X

FOUND_ROOT:
    ; Сохранить найденный корень (B)
    MOV [ROOTS_ADDR], B

    ; Продолжить поиск
    INC B
    JMP CHECK_X

END_PROGRAM:
    ; Завершение выполнения программы (бесконечный цикл, так как HALT не поддерживается)
END_PROGRAM:
    JMP END_PROGRAM

; Данные
M_ADDR:      DB 2
N_ADDR:      DB 1
ROOTS_ADDR:  DB 0
TEMP_X:      DB 0