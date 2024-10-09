def dating(boys, girls):
	if len(boys) != len(girls):
		print('Внимание, кто-то может остаться без пары.')
		return
	sortedBoys = boys
	sortedBoys.sort()
	sortedGirls = girls
	sortedGirls.sort()
	print('Идеальные пары:')
	for i, boy in enumerate(sortedBoys):
		print(f'{boy} и {sortedGirls[i]}')

dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard', 'Michael'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
