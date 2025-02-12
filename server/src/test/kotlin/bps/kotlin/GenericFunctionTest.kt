package bps.kotlin

//class BigThing(
//    val smallThing: SmallThing,
//    val otherStuff: Any,
//)
//
//abstract class SmallThing {
//    // I can't make this a generic function because the implementors are specific about the type
//    abstract fun bigThingFactory(otherStuff: Any): BigThing
//}
//
//class CategorySmallThing : SmallThing() {
//    override fun bigThingFactory(otherStuff: Any): BigThing = TODO()
//}
//
//open class RealSmallThing : SmallThing() {
//    override fun bigThingFactory(otherStuff: Any): BigThing = TODO()
//}
//
//class SpecialSmallThing : RealSmallThing() {
//    // NOTE this is why the type parameter A of Thing must be covariant.
//    override fun bigThingFactory(otherStuff: Any): BigThing = TODO()
//}
//
//class CallSite {
//
//    fun <A : SmallThing> getAThing(smallThing: A, otherStuff: Any): BigThing {
//        // no cast needed here but I'll need to cast BigThing.smallThing all over the place in the code elsewhere
//        val value: BigThing = smallThing.bigThingFactory(otherStuff)
//        // do other stuff
//        return value
//    }
//
//}


// NOTE this is what I have now.

//class BigThing<out A : SmallThing>(
//    val smallThing: A,
//    val otherStuff: Any,
//)
//
//// when I'm creating [BigThing]s, I would like to use polymorphism and just call [thingFactory] on
//// an existing instance of [SmallThing].
//abstract class SmallThing {
//    // I can't make this a generic function because the implementors are specific about the type
//    abstract fun bigThingFactory(otherStuff: Any): BigThing<*>
//}
//
//class CategorySmallThing : SmallThing() {
//    override fun bigThingFactory(otherStuff: Any): BigThing<CategorySmallThing> = TODO()
//}
//
//open class RealSmallThing : SmallThing() {
//    override fun bigThingFactory(otherStuff: Any): BigThing<RealSmallThing> = TODO()
//}
//
//class SpecialSmallThing : RealSmallThing() {
//    // NOTE this is why the type parameter A of Thing must be covariant.
//    override fun bigThingFactory(otherStuff: Any): BigThing<SpecialSmallThing> = TODO()
//}
//
//class CallSite {
//
//    fun <A : SmallThing> getAThing(smallThing: A, otherStuff: Any): BigThing<A> {
//        // What do I need to do so that this cast isn't needed?
//        // The compiler allows this cast but warns that it is unchecked.
//        @Suppress("UNCHECKED_CAST")
//        val value: BigThing<A> = smallThing.bigThingFactory(otherStuff) as BigThing<A>
//        // do other stuff
//        return value
//    }
//
//}

// NOTE this is what I want.  When I tried this in real code (Account and TransactionItem code) the JetBrains tools
//      started erroring out.  I want to try this again once the JetBrains tools mature.

// https://discuss.kotlinlang.org/t/generic-type-inference-not-working-as-i-hoped/29448

class BigThing<out A : SmallThing<A>>(
    val smallThing: A,
    val otherStuff: Any,
)

// when I'm creating [BigThing]s, I would like to use polymorphism and just call [thingFactory] on
// an existing instance of [SmallThing].
abstract class SmallThing<out A : SmallThing<A>> {
    // I can't make this a generic function because the implementors are specific about the type
    abstract fun bigThingFactory(otherStuff: Any): BigThing<A>
}

class CategorySmallThing : SmallThing<CategorySmallThing>() {
    override fun bigThingFactory(otherStuff: Any): BigThing<CategorySmallThing> = TODO()
}

open class RealSmallThing<out A : RealSmallThing<A>> : SmallThing<A>() {
    override fun bigThingFactory(otherStuff: Any): BigThing<A> = TODO()
}

class SpecialSmallThing : RealSmallThing<SpecialSmallThing>() {
    // NOTE this is why the type parameter A of Thing must be covariant.
    override fun bigThingFactory(otherStuff: Any): BigThing<SpecialSmallThing> = TODO()
}

class CallSite {

    fun <A : SmallThing<A>> getAThing(smallThing: A, otherStuff: Any): BigThing<A> {
        val value: BigThing<A> = smallThing.bigThingFactory(otherStuff)
        // do other stuff
        return value
    }

}
