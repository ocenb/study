def dating(boys, girls):
	if len(boys) != len(girls):
		print('Внимание, кто-то может остаться без пары.')
		return
	sortedBoys = boys
	sortedBoys.sort()
	sortedGirls = girls
	sortedGirls.sort()
	print('Идеальные пары:')
	for boy, girl in zip(sortedBoys, sortedGirls):
		print(f'{boy} и {girl}')

dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard', 'Michael'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
