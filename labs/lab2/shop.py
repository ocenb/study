products = input('Enter products list separated by comma: ').split(', ')
stores = {}
while True:
	store = input('Enter store name or "stop" to stop: ')
	if store == 'stop':
		break
	stores[store] = 0
	
	for product in products:
		price = int(input(f'Enter the price of {product} at that store: '))
		stores[store] += price

minCost = float('inf')
bestStore = None

print('Total costs: ')
for store, cost in stores.items():
	print(f'{store}: {cost}')
	if cost < minCost:
		minCost = cost
		bestStore = store

print(f'You can save the most at {bestStore}, cost = {minCost}')