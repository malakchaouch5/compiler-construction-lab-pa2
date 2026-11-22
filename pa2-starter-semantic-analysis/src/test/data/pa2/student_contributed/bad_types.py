# Type errors, one or two per line, and error recovery.  All declarations
# are correct, so the type checker runs on the whole program.  Comments say
# which error is expected and which type is inferred for the bad node.

class Shape(object):
    sides: int = 0
    label: str = "shape"

    def area(self: "Shape", scale: int) -> int:
        return self.sides * scale

class Square(Shape):
    def area(self: "Square", scale: int) -> int:
        return scale * scale

x: int = 0
b: bool = False
s: str = "abc"
items: [int] = None
shapes: [Shape] = None
more_shapes: [Shape] = None
sq: Square = None
o: object = None
w: int = True                  # literal of the wrong type

def twice(n: int) -> int:
    return n + n

def name_of(sh: Shape) -> str:
    if sh is None:
        return                 # `None` returned from a str function
    return sh.sides            # int returned from a str function

def local_assign() -> int:
    x = 5                      # semantic rule 8 (checked by the type checker)
    return x

items = [1, 2, 3]
sq = Square()

# ----- Recovery with the most specific type (see the write-up) --------------
x = (1 + "two") * 2            # `+` on int/str infers int: no error at `*`
b = (x == "zero") and True     # `==` infers bool: `and` is fine
x = items[True] + 1            # index error, but the element type is int
x = twice(b) - twice("no")     # bad arguments, but twice returns int
x = -s // 2                    # unary minus infers int
b = not x or b                 # `not` infers bool
x = sq.area(False) % 3         # bad argument, method still returns int
x = 1 if x else 2              # condition error, but the join is int
x = undefined_name + 1         # no specific type: object, then `+` fails

# ----- Operators --------------------------------------------------------------
x = None + 1
b = "a" < "b"
b = 1 is 1
b = [] == []
o = [] + [1]
o = items + [True]             # [int] + [bool] is fine: a list of object
s = s + 1

# ----- Lists and strings ------------------------------------------------------
x = x[0]
s[0] = "z"
items[s] = 1
o.missing = 1
o = items.length

# ----- Calls ------------------------------------------------------------------------
twice()
twice(1, 2)
x()
print(sq.perimeter())
len(items)[0]

# ----- Assignments ------------------------------------------------------------------
x = b = 1                      # only the bool target fails
x = s = y = "Error"            # `y` is no variable; error for `x` only
shapes = items
shapes = [sq, None]            # lists are invariant: [Square] is not [Shape]
shapes = [None]                # fine: [<None>] fits [Shape]
shapes = more_shapes = [None]  # but not in a multiple assignment

# ----- Statements ----------------------------------------------------------------------
if x:
    pass
while items:
    pass
for x in 10:
    pass
for b in items:
    pass

# ----- Semantic rule checked while type checking ----------------------------------
return x                       # semantic rule 10: return at the top level
