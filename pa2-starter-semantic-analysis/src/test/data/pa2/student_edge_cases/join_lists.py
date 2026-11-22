# Joins of list displays and conditional expressions.
class A(object):
    pass
class B(A):
    pass
class C(A):
    pass
x:object = None
x = [B(), C()]
x = [1, None]
x = [None, None]
x = [[], [1]]
x = [1, True]
x = [A(), None]
x = [[1], [None]]
x = B() if True else C()
x = [] if True else [1]
x = None if True else 1
