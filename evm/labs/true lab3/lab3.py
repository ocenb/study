import matplotlib.pyplot as plt
import numpy as np


def my_sign(x, k, delta):
    return np.sin(k * x + delta) > 0


def my_number(x, k, slovo):
    y = []
    for el in slovo:
        y += [int(el)] * k
    y += [0] * (len(x) - len(y))
    return np.array(y)


plt.figure(figsize=(25, 10))

xcoords = [el for el in range(0, 1001, 20)]
plt.yticks([])
plt.xticks(xcoords)
text = ["0", "1"] * 7
y = 0
for label in text:
    plt.text(-10, y, label)
    y += 0.75

t = np.arange(0, 1000, 1)
sdr = my_sign(t, 0.0786, 0)
x1 = my_sign(t, 0.314, 0)
x2 = my_number(t, 80, "01111010")
x3 = my_number(t, 40, "01111010")
x4 = my_number(t, 80, "11111111")
x5 = my_number(t, 40, "11111111")
x6 = my_number(t, 80, "00001111")
x7 = my_number(t, 40, "00001111")

x1[0], sdr[0] = True, True

plt.plot(t, x1 + 9)
plt.plot(t, x2 + 7.5)
plt.plot(t, sdr + 7.5, "--k", alpha=0.5)
plt.plot(t, x3 + 6)
plt.plot(t, sdr + 6, "--k", alpha=0.5)
plt.plot(t, x4 + 4.5)
plt.plot(t, sdr + 4.5, "--k", alpha=0.5)
plt.plot(t, x5 + 3)
plt.plot(t, sdr + 3, "--k", alpha=0.5)
plt.plot(t, x6 + 1.5)
plt.plot(t, sdr + 1.5, "--k", alpha=0.5)
plt.plot(t, x7)
plt.plot(t, sdr, "--k", alpha=0.5)

for xc in xcoords:
    plt.axvline(x=xc, linestyle="--", color="gray", alpha=0.5)

plt.legend(["gen", "SDR-7Ah", "DDR-7Ah", "SDR-FFh", "DDR-FFh", "SDR-0Fh", "DDR-0Fh"])
plt.show()
