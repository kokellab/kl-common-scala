package kokellab.utils.grammars.params

import kokellab.utils.grammars.IfElseIntegerGrammar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, PropSpec}

class ParametersTest extends PropSpec with TableDrivenPropertyChecks with Matchers {

	val p1 = DollarSignParam("$abc", false)
	val x1 = Map(p1 -> DollarSignSub(p1, List("50"), false))

	val p2 = DollarSignParam("$...abc", false)
	val x2 = Map(p2 -> DollarSignSub(p2, List(10, 11, 12, 13, 14, 15) map (_.toString), false))

	val p3 = DollarSignParam("$abc", false)
	val x3 = Map(p3 -> DollarSignSub(p1, List("10"), false))

	property(s"Find") {
		DollarSignParams.find("ab+$22*ac$zz$aa*ca$yy0$zzz", Set()) should equal (Set("$22", "$zz", "$aa", "$yy0", "$zzz") map (s => DollarSignParam(s, false)))
		DollarSignParams.find("$abcd$efg", Set("$abcd")) should equal (Set(DollarSignParam("$abcd", true), DollarSignParam("$efg", false)))
	}

	property(s"mapIndexToValue") {
		new RangeParameterizer().mapIndexToValue(Seq(0, 1, 2, 3, 4, 5), "$t+$abc", x1) should equal (Left("$t+50"))
		new RangeParameterizer().mapIndexToValue(Seq(0, 1, 2, 3, 4, 5), "$...abc", x2) should equal (Right(Seq("10", "11", "12", "13", "14", "15")))
	}

	property("map values onto grid") {
		new GridParameterizer(IfElseIntegerGrammar.eval).mapValuesOntoGrid("A1-A5", "$c+$abc", x1, 4, 8) map (z => z._1.index -> z._2) should equal (Map(1 -> 51, 2 -> 52, 3 -> 53, 4 -> 54, 5 -> 55))
		new GridParameterizer(IfElseIntegerGrammar.eval).mapValuesOntoGrid("A1*C2", "$abc + 10*$r + $c", x3, 4, 8) map (z => (z._1.row, z._1.column) -> z._2) should equal (
			Map((1,1) -> 21, (1,2)-> 22, (2,1) -> 31, (2,2)-> 32, (3,1) -> 41, (3,2)-> 42)
		)
	}

}
