# Type annotations may refer to classes defined later.
x:A = None
def f(a:B) -> [C]:
    return None
class A(object):
    pass
class B(A):
    pass
class C(B):
    pass
x = B()
print(f(C()))
