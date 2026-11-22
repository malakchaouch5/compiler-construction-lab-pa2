# Assignment compatibility of None, [] and [None].
class A(object):
    pass
a:A = None
l:[A] = None
m:[A] = None
n:[int] = None
o:object = None
l = []
l = [None]
l = [a, None]
o = [None]
n = []
a = None
l = m = [a]
