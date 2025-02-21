import matplotlib.pyplot as plt
import numpy as np

h = 0.000001
t = 0.001
x = np.arange(0, t - h, h)
y = [0] * 1000
u = [0] * 1000
v = [0] * 1000

for k in range(0, 5):
    for i in range(0, 200):
        if i < 100:
            y[200 * k + i] = 0.5
        else:
            y[200 * k + i] = 4.5

u[0] = 0.5

for i in range(0, 999):
    u[i + 1] = u[i] + h * (y[i] - u[i]) / 0.00002
for i in range(0, 1000):
    u[i] = u[i] + (0.6 * np.random.rand() - 0.3)

v[0] = 0.5

for i in range(0, 999):
    if u[i] < 4 and u[i + 1] > 4:
        v[i + 1] = 4.5
    else:
        if u[i] > 2 and u[i + 1] < 2:
            v[i + 1] = 0.5
        else:
            v[i + 1] = v[i]

plt.plot(x, y)
plt.plot(x, v)
plt.show()
