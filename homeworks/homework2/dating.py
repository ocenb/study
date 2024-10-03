def dating(boys, girls):
	if len(boys) != len(girls):
		print('Внимание, кто-то может остаться без пары.')
		return
	sortedBoys = boys
	sortedBoys.sort()
	sortedGirls = girls
	sortedGirls.sort()
	index = 0
	print('Идеальные пары:')
	for boy in sortedBoys:
		print(f'{boy} и {sortedGirls[index]}')
		index += 1

dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
dating(['Peter', 'Alex', 'John', 'Arthur', 'Richard', 'Michael'], ['Kate', 'Liza', 'Kira', 'Emma', 'Trisha'])
