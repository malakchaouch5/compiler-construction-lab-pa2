# A small zoo simulation that is semantically valid and well-typed.  It
# exercises (almost) every typing rule of ChocoPy: literals, variables,
# all operators, lists, strings, classes with inheritance and overriding,
# constructors, attributes, methods, functions, nested functions with
# global/nonlocal declarations, and every kind of statement.

# ----- Global variables of every kind of type --------------------------------
count: int = 0
total_weight: int = 0
verbose: bool = False
zoo_name: str = "ChocoZoo"
keeper: object = None
weights: [int] = None
grid: [[int]] = None
names: [str] = None
first_animal: "Animal" = None
pens: ["Pen"] = None


# ----- Classes ----------------------------------------------------------------
class Animal(object):
    name: str = ""
    weight: int = 0
    hungry: bool = True
    friend: "Animal" = None

    def __init__(self: "Animal"):
        self.name = "unknown"

    def sound(self: "Animal") -> str:
        return "..."

    def describe(self: "Animal") -> str:
        return self.name + " says " + self.sound()

    def feed(self: "Animal", amount: int) -> int:
        if amount <= 0:
            return self.weight
        elif self.hungry:
            self.hungry = False
            self.weight = self.weight + amount // 2
        else:
            self.weight = self.weight + amount % 3
        return self.weight


class Cat(Animal):
    lives: int = 9

    # Overrides keep the signature (except for the type of self).
    def sound(self: "Cat") -> str:
        return "meow"


class Lion(Cat):
    pride: [Animal] = None

    def __init__(self: "Lion"):
        self.name = "lion"
        self.pride = []

    def sound(self: "Lion") -> str:
        return "ROAR"

    def lead(self: "Lion", other: Animal) -> int:
        self.pride = self.pride + [other]
        return len(self.pride)


class Parrot(Animal):
    words: [str] = None

    def sound(self: "Parrot") -> str:
        if self.words is None:
            return "squawk"
        return self.words[0]


class Pen(object):
    animals: [Animal] = None
    label: str = "pen"

    def add(self: "Pen", a: Animal) -> "Pen":
        if self.animals is None:
            self.animals = [a]
        else:
            self.animals = self.animals + [a]
        return self

    def heaviest(self: "Pen") -> Animal:
        best: Animal = None
        a: Animal = None
        for a in self.animals:
            if best is None or a.weight > best.weight:
                best = a
        return best


# ----- Functions ----------------------------------------------------------------
def make_cat(name: str) -> Cat:
    c: Cat = None
    c = Cat()
    c.name = name
    return c


def sum_list(items: [int]) -> int:
    result: int = 0
    i: int = 0
    while i < len(items):
        result = result + items[i]
        i = i + 1
    return result


def reverse(s: str) -> str:
    out: str = ""
    ch: str = ""
    for ch in s:
        out = ch + out
    return out


def classify(n: int) -> str:
    if n < 0:
        return "negative"
    elif n == 0:
        return "zero"
    elif n % 2 == 0 and not n > 100:
        return "small even"
    else:
        return "other"


def counter() -> int:
    calls: int = 0
    global count

    def tick(step: int) -> int:
        nonlocal calls
        calls = calls + step
        return calls

    def nested_total() -> int:
        def deep() -> int:
            nonlocal calls
            calls = calls * 2
            return calls
        return deep() + tick(0)

    count = count + 1
    tick(1)
    tick(-(-2))
    return nested_total()


def first_or_none(pen: Pen) -> Animal:
    if len(pen.animals) > 0:
        return pen.animals[0]
    return None


# ----- Top-level statements --------------------------------------------------------
first_animal = Animal()
first_animal.name = "generic"
keeper = first_animal

weights = [3, 1, 4, 1, 5]
weights[0] = weights[1] = 9
grid = [[1, 2], [3], []]
grid[2] = [sum_list(weights)]
names = ["Tom", "Leo"] + ["Polly"]

pens = [Pen(), Pen()]
pens[0].add(make_cat(names[0])).add(Lion())
pens[1].label = "birds"
pens[1].add(Parrot())

total_weight = pens[0].heaviest().feed(10) + first_animal.feed(-1)
verbose = total_weight >= 5 or zoo_name == "Zoo" and verbose != True

# Joins: the list of a Cat and a Lion is a list of Cat, None fits into
# lists of objects, and conditional expressions join their branches.
first_animal.friend = [make_cat("Kit"), Lion()][1] if verbose else None
keeper = [None, Lion(), 3] if keeper is None else "nobody"
print([first_animal, first_or_none(pens[1])])

count = counter()
print(classify(count))
print(reverse(zoo_name)[0] + zoo_name[len(zoo_name) - 1])
print(Lion().lead(Parrot()))

for count in weights:
    if not count != 1:
        pass
    else:
        print(count)

while count > 0:
    count = count - 3

print(input() + "!")
