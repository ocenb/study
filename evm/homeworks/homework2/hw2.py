import itertools
import matplotlib.pyplot as plt
import numpy as np

# Генерация таблицы истинности
table = []
for x in itertools.product([0, 1], repeat=4):
    x1, x2, x3, x4 = x
    active = None
    
    if x1 == 1:
        active = 0
    elif x2 == 1:
        active = 1
    elif x3 == 1:
        active = 2
    elif x4 == 1:
        active = 3
    else:
        active = None
    
    if active is not None:
        a1 = (active >> 1) & 1
        a2 = active & 1
        eo = 0
    else:
        a1 = 0
        a2 = 0
        eo = 1
    
    table.append({
        'X1': x1, 'X2': x2, 'X3': x3, 'X4': x4,
        'a1': a1, 'a2': a2, 'EO': eo
    })

# Визуализация
index = np.arange(len(table))

plt.figure(figsize=(12, 8))

# Входные сигналы
plt.subplot(2, 1, 1)
signals_in = ['X1', 'X2', 'X3', 'X4']
for signal in signals_in:
    values = [t[signal] for t in table]
    plt.step(index, values, where='post', label=signal, linewidth=2)

plt.title('Входные сигналы (X1-X4)')
plt.ylabel('Уровень')
plt.xticks(index)
plt.legend(loc='upper right')
plt.grid(True, linestyle='--', alpha=0.7)
plt.xlim(-0.5, 15.5)
plt.ylim(-0.1, 1.1)

# Выходные сигналы
plt.subplot(2, 1, 2)
signals_out = ['a1', 'a2', 'EO']
for signal in signals_out:
    values = [t[signal] for t in table]
    plt.step(index, values, where='post', label=signal, linewidth=2)

plt.title('Выходные сигналы (a1, a2, EO)')
plt.xlabel('Номер комбинации')
plt.ylabel('Уровень')
plt.xticks(index)
plt.legend(loc='upper right')
plt.grid(True, linestyle='--', alpha=0.7)
plt.xlim(-0.5, 15.5)
plt.ylim(-0.1, 1.1)

plt.tight_layout()
plt.show()
