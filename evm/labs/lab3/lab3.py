import numpy as np
import matplotlib.pyplot as plt

h = 0.000001     # Шаг по времени, 1 мкс
t_c = 0.00064  # Время моделирования, 1 мс
time = np.arange(0, t_c, h)  # Массив времени

freq1 = 5000  # Частота сигнала 5 кГц
freq2 = 10000 # Частота сигнала 10 кГц

# Постоянные времени
T1 = 0.00001
T2 = 0.00002

# Границы запрещённой зоны
Umin1, Umax1 = 1.5, 3.5
Umin2, Umax2 = 2.0, 4.0

# Логические уровни
logic_0 = 0
logic_1 = 5

# Генерация прямоугольных сигналов
def generate_square_wave(freq, time, logic_0, logic_1):
    period = 1 / freq
    signal = [(logic_1 if (t % period) < (period / 2) else logic_0) for t in time]
    return np.array(signal)

a1 = generate_square_wave(2/t_c, time, logic_0, logic_1)
a2 = generate_square_wave(1/t_c, time, logic_0, logic_1)

x1 = generate_square_wave(32/t_c, time, logic_0, logic_1)
x2 = generate_square_wave(16/t_c, time, logic_0, logic_1)
x3 = generate_square_wave(8/t_c, time, logic_0, logic_1)
x4 = generate_square_wave(4/t_c, time, logic_0, logic_1)

e1,e2,e3,e4 = [],[],[],[]

OR_lat = 1
AND_lat = 1
NOT_lat = 1

def clamp(maximum, minimum, number):
    return max(minimum, min(maximum, number))

for t in range(len(time)):
    t2 = clamp(len(time) - 1, 0, t - (OR_lat + NOT_lat))
    logic1, logic2 = a1[t2], a2[t2]
    if not (logic1 or logic2):
        e1.append(logic_1)
    else:
        e1.append(logic_0)

    t2 = clamp(len(time) - 1, 0, t - AND_lat)
    t3 = clamp(len(time) - 1, 0, t - (NOT_lat + AND_lat))
    logic1, logic2 = a1[t2], a2[t3]
    if logic1 and not logic2:
        e2.append(logic_1)
    else:
        e2.append(logic_0)
        
    logic1, logic2 = a1[t3], a2[t2]
    if not logic1 and logic2:
        e3.append(logic_1)
    else:
        e3.append(logic_0)

    t2 = clamp(len(time) - 1, 0, t - AND_lat)
    logic1, logic2 = a1[t2], a2[t2]
    if logic1 and logic2:
        e4.append(logic_1)
    else:
        e4.append(logic_0)

F = []

for t in range(len(time)):
    t2 = clamp(len(time) - 1, 0, t - (AND_lat + OR_lat))
    F.append((e1[t2] and x1[t2]) or (e2[t2] and x2[t2]) or (e3[t2] and x3[t2]) or (e4[t2] and x4[t2]))


plt.figure(figsize=(9, 9))

# Прямоугольные импульсы
plt.subplot(7, 1, 7)
plt.plot(time, a2, label='a2')
plt.xlabel("Время (с)")
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 6)
plt.plot(time, a1, label='a1')
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 5)
plt.plot(time, x4, label='x4')
plt.plot(time, e4, label='e4 с зад.')
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 4)
plt.plot(time, x3, label='x3')
plt.plot(time, e3, label='e3 с зад.')
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 3)
plt.plot(time, x2, label='x2')
plt.plot(time,e2, label='e2 с зад.')
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 2)
plt.plot(time, x1, label='x1')
plt.plot(time, e1, label='e1 с зад.')
plt.ylabel("Вольт")
plt.grid()
plt.legend()

plt.subplot(7, 1, 1)
plt.plot(time, F, label='F')
plt.ylabel("Вольт")
plt.legend()
plt.grid()

plt.tight_layout()
plt.show()