# Module yafrl-core

**Y**et **A**nother **F**unctional **R**eactive **L**ibrary (**yafrl**) 
 is a utility library extending the capabilities of [kotlinx-coroutines](https://github.com/Kotlin/kotlinx.coroutines) by
 following an alternative philosophy of how reactive programming should work. In particular, we offer "better-behaved" versions of constructs such as [`Flow`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/) and 
 [`StateFow`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/),

`yafrl` is based on the [original ideas](https://futureofcoding.org/essays/dctp.html) of _functional reactive programming_, and is
 heavily inspired by the Haskell library [Reflex](https://hackage.haskell.org/package/reflex).

As such, tutorials on Reflex like this excellent one from [Queensland FP Labs](https://qfpl.io/posts/reflex/basics/introduction/)
 would prove to be a useful introduction to the topic, provided one is willing to read some Haskell!

However, for those who are not, we provide a simple introduction below, together with 3 improvements that
 `yafrl` offers over `kotlinx-coroutine`.

## Why `yafrl`?

`kotlinx-coroutines` (and specifically the `kotlinx.coroutines.flow` part) is decent for what it is 
 -- and for those who have previously used frameworks like `rxJava`, it is definitely a breath of
 fresh air. 

Constructs like `Flow` and `StateFlow` allow you to define loosely coupled business logic components with 
 reactive state that can be consumed by front-end components in a clean way with patterns such as MVVM
(Model-View-ViewModel) and MVI (Model-View-Intent). And `Flow`s also integrate very well with Kotlin's
 [structured concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency), 
 providing innumerable benefits for application developers.

`kotlinx-coroutines` is also a massive improvement semantically over "conventional" reactive frameworks in that
 it actually provides a mechanism for _reactive states_ -- that is, `StateFlow`. In other reactive frameworks such as
 rxJava, while similar concepts could be implemented, the lack of a distinct type made trying to implement
 constructs like that finicky and error-prone. However, `kotlinx-coroutines` still has some frustrating issues that
 `yafrl` solves.

### More convenient reactive state operations

One of the issues you may have encountered if you've been using `kotlinx-coroutines` for a while is the fact that
 there is no `StateFlow<A>.map((A) -> B): StateFlow<B>` operator. If we try to `map` a `StateFlow`, it will
 just use `Flow<A>.map((A) -> B): Flow<B>`, with `StateFlow<A>` being implicitly upcast to `Flow<A>`.

`map` is a generic idiom coming from functional programming technically known as a [Functor](https://wiki.haskell.org/index.php?title=Functor) 
 -- and one of the key requirements for a `map` operation to be a functor is that when we `map` something, we get out the
 same type of container that we put in! (a type of _closure_ property) 

Similar issues hold for other methods of `Flow` / `StateFlow` -- such as `combine`. This is actually another
 functional programming idiom called [Applicative](https://wiki.haskell.org/index.php?title=Applicative_functor),
 which defines a common API for data structures that can "combined" or "zipped" together with other data structures 
 of the same kind.

kotlinx-coroutines provides the [stateIn](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/state-in.html) operator 
 for converting `Flow`s back into `StateFlow`s again to help solve this problem -- but in code where [StateFlow]s are
 often manipulated with functional operations, this starts to get old very quickly.

### Solid mathematical foundations

The bigger issue, however, is that kotlinx-coroutines is built on very _operational_ foundations. In other words,
 in order to understand how any concept relating to `Flow`s works, you much understand very low-level imperative
 concerns such as how the `Flow` interacts with subscribers.

While basic usages of `yafrl` may look similar to kotlinx-coroutines on the surface -- in practice it is very different
 because it is based on elegant mathematical foundations that give you an easy to think about mental model of the
 constructs in the library that you can expect to translate directly into actual behavior at runtime, without
 having to worry about low-level details.

In fact, the biggest difference you'll notice with `yafrl` code as compared to kotlinx-coroutines is that while
 there is a `collect` operator on `State`s and `Event`s for convenience’s sake (so you can interoperate with
 other kotlin libraries expecting `Flow`s and `StateFlow`s more easily) -- its use is strongly discouraged in
 both the _business logic_ and _presentation logic_ layers of your application. If you feel tempted to use `collect`
 for anything other than debugging, or integrating with some kind of external framework -- you're probably doing
 more harm than good, and should ask yourself if there's a better way.

### Easy to test

Even if none of the above has convinced you, one of the most damning issues with `kotlinx-coroutines` might
 well be the fact that code using `Flow`s and `StateFlow`s have the tendency to be flaky, slow, as well as just 
 plain difficult to test!

We have several examples of this in our test suite under the `negative_tests` package -- showing examples where
 `kotlinx-coroutines` behaves in unexpected ways, requiring things like having to insert manual delays in order
 to get tests to pass, and even how even the `kotlinx-coroutines-test` module does not provide sufficent
 tools to be able to sufficiently ameliorate these problems.

Since `yafrl` is synchronous by default, writing tests for `yafrl` code is just as easy as writing tests
 for normal synchronous Kotlin -- while still providing integration with `kotlinx-coroutine`'s asynchronous
 features when necessary.

# Package io.github.yafrl

User-facing (public) APIs for `yafrl`.

# Package io.github.yafrl.timeline

Internal `yafrl` APIs -- not intended to be used directly
 by users of the library in most use-cases.