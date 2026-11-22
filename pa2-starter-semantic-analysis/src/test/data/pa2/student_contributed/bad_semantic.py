# Violations of the semantic rules of Section 5.2 of the PA2 specification.
# Every program entity is involved in at most one violation, so the errors
# do not conflict.  Rules 8 and 10 are checked during type checking, which
# only runs when the declarations are correct; they are therefore tested in
# bad_types.py.

# ----- Rule 1: no duplicate declarations in the same scope -------------------
count: int = 0
count: int = 1                       # duplicate global variable

def helper(a: int, a: bool) -> int:  # duplicate parameter
    b: int = 1
    b: str = "b"                     # duplicate local variable
    return a

class Vehicle(object):
    wheels: int = 4
    wheels: int = 3                  # duplicate attribute

    def drive(self: "Vehicle") -> int:
        return self.wheels

    def drive(self: "Vehicle") -> int:   # duplicate method
        return 0

def len(x: object) -> int:           # predefined function `len`
    return 0

class str(object):                   # predefined class `str`
    pass

# ----- Rule 2: names may not shadow class names ------------------------------
def make(Vehicle: int) -> int:       # parameter named like a class
    object: bool = True              # local variable named like a class
    def int() -> bool:               # nested function named like a class
        return True
    return 0

# ----- Rule 3: global / nonlocal declarations -----------------------------------
total: int = 0

def outer() -> int:
    inner_value: int = 1
    global total                     # OK
    global missing                   # no such global variable
    global outer                     # a function, not a variable

    def inner() -> int:
        nonlocal inner_value         # OK
        nonlocal total               # a global, not a nonlocal variable
        nonlocal nothing             # no such variable
        return inner_value

    return inner()

# ----- Rule 4: super-classes ------------------------------------------------------
class Car(Truck):                    # Truck is defined later
    pass

class Bike(total):                   # total is a variable, not a class
    pass

class Number(int):                   # int, bool and str cannot be extended
    pass

class Truck(Vehicle):
    # ----- Rule 5: attributes may not re-define inherited members -----------
    wheels: int = 6                  # re-defines an inherited attribute
    drive: bool = False              # re-defines an inherited method

class Bus(Vehicle):
    # ----- Rule 6: the first parameter of a method ---------------------------
    def board(self: "Vehicle") -> int:   # self must be a Bus
        return 0

    def leave() -> int:              # no parameter at all
        return 0

class Tram(Vehicle):
    # ----- Rule 7: overriding with a different signature ---------------------
    def drive(self: "Tram", speed: int) -> int:   # extra parameter
        return speed

class Train(Vehicle):
    def drive(self: "Train") -> bool:            # different return type
        return True

    def wheels(self: "Train") -> int:            # rule 5: method vs attribute
        return 8

# ----- Rule 9: missing return on some path -----------------------------------------
def sign(n: int) -> int:
    if n > 0:
        return 1
    elif n < 0:
        return -1

def loop_only(n: int) -> str:
    while n > 0:
        return "positive"

# ----- Rule 11: type annotations must name classes ----------------------------------
garage: [Garage] = None              # no class named Garage

def park(v: Plane) -> Hangar:        # no classes Plane and Hangar
    spot: [[Spot]] = None            # no class named Spot
    return None

pass
