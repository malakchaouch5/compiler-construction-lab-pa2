# PA2 presentation: speaker notes

Group 4: Omar Kamel and Malak Chaouch. Notes for each slide of PA2_Group4_Presentation.pdf. We built everything together; for the talk, Omar presents slides 1-7 and 11-12 and Malak slides 8-10 and 13-15. Together about 15 minutes.

## 1. Semantic analysis and type checking for ChocoPy

Omar opens. Introduce the group and the goal of PA2: take the AST of a parsed program, report semantic errors, and give every expression its type. We skip what type checking is, because everyone built the same thing, and go straight to the problems that cost us the most time. I present the structure, the declarations and the testing; Malak presents the type checker, error recovery and what we found in the reference. We wrote all of it together.

## 2. Four problems that took most of our time

Omar. One: the data structures, a symbol table for every function and a class hierarchy with inherited members. Two: which error wins. Only one error is kept per AST node, so when a declaration breaks two rules, the order of the checks decides the message, and the hidden tests need every message of the reference. Three: recovery, inferring the most specific type so that one mistake gives one message. Four: knowing that it is right. The results: all 78 local tests pass, and on 36,657 programs we report exactly the errors of the reference and not a single extra one. 3,410 of those programs are correct, and for every one the complete typed AST is identical.

## 3. Three passes over the AST

Omar. This is the answer to question 1 of the write-up. Pass 1, the DeclarationAnalyzer, builds the global symbol table, the class hierarchy and one symbol table per function body, and checks rules 1 to 7 and 9. Pass 2, the TypeAnnotationChecker, only checks that every class in a type annotation exists, rule 11. Pass 3, the TypeChecker, infers types and checks the typing rules and the two statement rules, 8 and 10. Two decisions here came from the reference. First, like the reference we do not run the type checker if passes 1 or 2 found an error: the symbol tables of a wrong program are incomplete, and the reference does the same. Second, pass 2 is separate because the reference checks annotations everywhere, even inside a duplicate method whose body it otherwise ignores. Subtyping, assignability and the join are written once, in ClassHierarchy.

## 4. A symbol table per function, filled in two steps

Omar. The pass works in two steps. Step 1 binds every global name in program order: variables to their type, functions to a FuncType, classes to a ClassInfo. Because it is in order, a super-class must be defined before it is used, which is rule 4. Step 2 analyzes the bodies. At that point every global name is known, so in the example f can call h, which is defined after it. Each body gets a SymbolTable whose parent is the enclosing scope: f's table has y, x from the global declaration, and the nested function g; g's table has y from nonlocal. We keep a map from each FuncDef to its table, so the type checker reuses the same tables instead of building the scopes a second time.

## 5. Inheritance, assignability and the join

Omar. A ClassInfo copies all attributes and methods of its super-class when it is created, so a lookup is one map access, and the inherited method keeps the type of the class that defined it, which is what the reference prints. Assignability is the relation from the manual: subclass, None into everything except int, bool and str, the empty list into every list, and the list of None into lists that can hold None. The join returns one type if the other is assignable to it, else the closest common super-class, else object. The table shows what the reference expects: a list of B and C is a list of A, 1 and None give object, and the empty list joins with a list of int to a list of int. One surprise: None and the empty list behave like classes below object, so None.__init__() is fine and has type None.

## 6. Only members that pass their checks are added

Omar. This is the core of "which error wins". Our first version added every member to the class and then reported the second method m in B as a duplicate. The reference reports "Method overridden with different type signature" for both. The reason: a member is only added to the class if it passes all its checks, and a later member is a duplicate only if an earlier one with that name was added. The first m in B has the wrong return type, so it is not added, and the second m is checked against A again. The same holds in C: the first m has no self parameter, so the second m is fine. We also had to find the order: re-defined attribute, then the signature, then the self parameter. Found with the differential tester, confirmed with a minimal program each time.

## 7. nonlocal: the nearest declaration decides

Omar. Nonlocal was the other surprise. Two levels up is fine. A local x in f that shadows a global x is fine and has the local type str. But if f declares x global, nonlocal x in an inner function is an error. And nonlocal a for a local a of the same function is reported as a duplicate, not as "not a nonlocal variable". All of this is explained by one rule: search from the current scope outwards; the first table that declares x decides, and it must hold a variable that is neither global nor declared global. If that table is the current one, the duplicate check fires afterwards. We also had a bug of our own here: a duplicate global x that was rejected was still recorded as global, so a later nonlocal x failed. Now Malak continues with the type checker.

## 8. Drop the failing premise, keep the conclusion

Malak. This is question 3 of the write-up. If a premise of a typing rule fails, we report it and give the expression the type of the conclusion without that premise. The table uses lines of our bad_types.py. On line 42, plus on an int and a string is an error, but one operand is an int, so the result is int, and the multiplication and the assignment are fine: one error. If we inferred object for every ill-typed expression, the multiplication would fail on object and the assignment of object to int would fail too: three errors for one mistake. The same for the comparison, which is always bool, the bad list index, which still has the element type, and the calls with bad arguments, which still return int. Line 50 shows the other case: an unknown name has no specific type, so object it is, and the plus fails as well. Plus is the one rule where the specification decides: int if one operand is int, otherwise object.

## 9. Three places an assignment error can go

Malak. Multiple assignment is a special case of recovery. Take the example from the specification, x = y = z = "Error". The error that z is not a variable belongs to the target z. The type mismatch is reported once, at the whole statement, for the leftmost target that does not fit: x. Nothing is said about y, because the statement already has its message. The code first checks the value and every target, then the targets from left to right, and stops at the first mismatch. The rule that a multiple assignment may not assign a list of None comes last: we first had it before the target check, and the differential tester showed that the reference reports the type mismatch instead.

## 10. Small rules we only found by comparing

Malak. Here are the details where our first version differed. Assigning to s at index True, where s is a string: the reference reports the bad index, because it first checks the target like a normal index expression, and only if that is fine does it say that str is not a list type. For an int target it says "Cannot index into type int". After "Cannot iterate over value of type int", the loop variable is not checked at all. None.__init__() is fine, because None inherits from object. An annotation with an unknown class inside a duplicate method is still reported, which is why the annotations are a separate pass. And the target type comes before the list-of-None rule. None of this is in the specification; we only found it by comparing with the reference.

## 11. The reference analysis as a test oracle

Omar. How did we find all this? The 78 local tests all passed early, so they could not tell us more. We wrote a generator for random ChocoPy programs: classes with inheritance and overriding, nested functions with global and nonlocal, every statement and expression, with expressions chosen by type and a rate of deliberate mistakes between 0 and 40 percent. A small Java harness runs the reference and our analysis in one JVM. For a correct program the whole typed AST must be equal; for a wrong program every reference error with its line and column must be ours, and we also list errors only we report. The result on 39,500 generated programs plus our tests and probes: zero failures and zero extra errors. The tester found about a dozen differences in our code, all about the order of checks and recovery, the ones on slides 6 to 10.

## 12. From a difference to a fix

Omar. Every fix followed the same four steps. The harness prints a difference: here the reference says "Method overridden" at line 24, and we said "Duplicate". We cut the generated file down to a minimal program, ask the reference with --pass=rr, and only change our rule when we understand the behaviour. Then we rerun all programs, because a fix for one case often broke another, and keep the minimal program as a test in student_edge_cases. There are 16 of them now.

## 13. What we found in the reference and the material

Malak. Three real bugs in the reference. First, a parameter that shadows a class name is dropped from the method's signature, so an identical override in a subclass is reported as different, and a really different one is not reported. Second, the body of a duplicate method is not checked, except for missing returns of nested functions. Third, for loops: after "Cannot iterate", an undefined loop variable is not reported, and "Not a variable" is placed on the whole for statement instead of the name. Then a small inconsistency in the messages, got None versus got type None, and some mistakes in the specification and the starter README. Each one has an input and the expected output in our README.

## 14. Two quirks we copied on purpose

Malak. Two of these bugs we did not fix in our code; we copied them. The hidden tests require every error of the reference. If our signature kept the rejected parameter, we would miss the reference's override error in the first example. So our signature leaves out rejected parameters too. This only changes error messages: with a declaration error, the type checker does not run. The same for duplicate method bodies. Both are conflicting violations, which the specification does not grade, but copying them gives us no extra and no missing errors on all 36,657 programs. We reported both as bugs.

## 15. How we worked

Malak. We did not split the work by component. We designed the three passes together and wrote the declaration analysis, the class hierarchy, the type checker and the recovery in joint sessions. The tests, the program generator, the differential tester and the README were also done together, and every part was reviewed by both of us. That is why either of us can answer questions about any part. About 35 hours in total.

## 16. Where each part lives in the code

Backup slide for the Q&A: the file and lines of each part, so we can open the code quickly when asked.

## 17. Thank you. Questions?

Both. Thank the audience and invite questions on scopes, classes, type checking or testing. The commands at the bottom build the project and run the sample tests.
