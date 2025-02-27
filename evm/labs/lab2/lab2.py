import matplotlib.pyplot as plt
import numpy as np

t_c = 0.001
h = 0.000001
time = np.arange(0, t_c, h)

freq1 = 2000
freq2 = 5000
freq3 = 10000

T1 = 0.00001
T2 = 0.00002

Umin, Umax = 2, 4

logic_0 = 0
logic_1 = 5

def saw_line(freq, time, logic_0, logic_1):
    switch = logic_0
    period = 1 / freq
    signal, signal2 = [], []
    for t in time:
        height = logic_1 - logic_0
        var = t / period * 2 * height
        var2 = t // period * 2 * height
        if (t % period) < (period / 2):
            t = var - var2 + logic_0
            signal.append(t)
            if t > Umax:
                switch = logic_1
        else:
            t = height - var + (var2 + height) + logic_0
            signal.append(t)
            if t < Umin:
                switch = logic_0
        signal2.append(switch)
    return np.array(signal), np.array(signal2)

plt.figure(figsize=(12, 12))

signal_2kHz, signal_2kHz_sqr = saw_line(freq1, time, logic_0, logic_1)

plt.subplot(3, 1, 1)
plt.plot(time, signal_2kHz, label="Треугольный сигнал (2 kHz)")
plt.plot([0,t_c], [Umin, Umin], label="Верхний предел")
plt.plot([0,t_c], [Umax, Umax], label="Нижний предел")
plt.plot(time, signal_2kHz_sqr)
plt.title("Треугольные импульсы")
plt.xlabel("Время (с)")
plt.ylabel("Напряжение (В)")
legend = plt.legend(loc='center right')
plt.grid()

signal_5kHz, signal_5kHz_sqr = saw_line(freq2, time, logic_0, logic_1)

plt.subplot(3, 1, 2)
plt.plot(time, signal_5kHz, label="Треугольный сигнал (5 kHz)")
plt.plot([0,t_c], [Umin, Umin], label="Верхний предел")
plt.plot([0,t_c], [Umax, Umax], label="Нижний предел")
plt.plot(time, signal_5kHz_sqr)
plt.title("Треугольные импульсы")
plt.xlabel("Время (с)")
plt.ylabel("Напряжение (В)")
legend = plt.legend(loc='center right')
plt.grid()

signal_10kHz, signal_10kHz_sqr = saw_line(freq3, time, logic_0, logic_1)

plt.subplot(3, 1, 3)
plt.plot(time, signal_10kHz, label="Треугольный сигнал (10 kHz)")
plt.plot([0,t_c], [Umin, Umin], label="Верхний предел")
plt.plot([0,t_c], [Umax, Umax], label="Нижний предел")
plt.plot(time, signal_10kHz_sqr)
plt.title("Треугольные импульсы")
plt.xlabel("Время (с)")
plt.ylabel("Напряжение (В)")
legend = plt.legend(loc='center right')
plt.grid()
plt.tight_layout()
plt.show()
