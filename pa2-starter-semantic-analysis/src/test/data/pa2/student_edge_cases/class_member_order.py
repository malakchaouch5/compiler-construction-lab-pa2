# Only members that pass their checks are added to a class.
class A(object):
    def m(self:"A") -> int:
        return 1
class B(A):
    def m(self:"B") -> str:
        return ""
    def m(self:"B") -> bool:
        return True
class C(object):
    def m(x:int):
        pass
    def m(self:"C"):
        pass
