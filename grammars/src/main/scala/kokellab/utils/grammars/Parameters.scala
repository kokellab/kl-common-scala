package kokellab.utils.grammars

import java.util.regex.Pattern

import scala.collection.SeqLike
import scala.util.matching.Regex

/**
  * Code for a specific purpose.
  */
object Parameterizations {

	def mapValuesOntoGrid(range: String, valueExpression: String, substitutionsText: String, nRows: Int, nColumns: Int, defaultValue: String = "", seed: Option[Int]): Map[PointLike, String] = {
		val cells: Seq[PointLike] = AlphanumericRangeGrammar.eval(range, nRows, nColumns)
		val tuple = mapIndexToValue(cells map (_.index), valueExpression, substitutionsText) match {
			case Left(singleExpression) =>
				cells map { cell => cell -> {
					val replaced = DollarSignParams.substitute(singleExpression, Map("$r" -> cell.row.toString, "$c" -> cell.column.toString, "$i" -> cell.index.toString))
					IfElseGrammar.eval(replaced, seed = seed) match {
						case None => defaultValue
						case Some(v) => v.toString
					}
				}}
			case Right(manyValues) => cells zip manyValues
		}
		tuple.toMap
	}

	/**
	  *
	  * @param range
	  * @param valueExpression
	  * @param substitutionsText
	  * @param pattern
	  * @return just a String if the value applies to everything in the range; a list otherwise
	  */
	def mapIndexToValue(range: Seq[Int], valueExpression: String, substitutionsText: String, pattern: Pattern = Pattern.compile(".*")): Either[String, Seq[String]] = {

		val valueParams = DollarSignParams.find(valueExpression, Set.empty)
		val lengths = (valueParams map {p =>
			if (p.isList) p.name -> range.size else p.name -> 1
		}).toMap
		val substitutions: Map[Param, DollarSignSub] = parse(substitutionsText, valueParams.toSet, lengths, pattern)

		/*
		Note that we've checked the lengths above. We don't need to verify the substitutions object after that.
		There are two cases:
		    A) There are no list types $...x is defined in the expression. Then we replace all of the parameters and return.
		    B) There is exactly one list type $...x defined in the expression. Then we substitute each array index for each range index.
		 */

		if (valueParams forall (p => !p.isList)) {  // case A
			val replaced = substitutions map (k => (k._1.name, k._2.values.head))
			Left(DollarSignParams.substitute(valueExpression, replaced))

		} else if (valueParams.size == 1 && valueParams.head.isList) {  // case B
			Right(substitutions.head._2.values)

		} else {
			throw new GrammarException(s"Substitutions in '$substitutionsText' have the wrong type (list or non-list) for expression '$valueExpression'")
		}
	}

	def parse(text: String, pars: Set[DollarSignParam], lengths: Map[String, Int], pattern: Pattern): Map[Param, DollarSignSub] = {

		text.split("\n") flatMap { s =>
			if (s.trim.isEmpty) None else Some {
				if (!(s contains '=')) throw new GrammarException(s"Non-empty line $s does not contain an equals sign")
				val key = s.substring(0, s.indexOf('=')).trim
				val value = s.substring(s.indexOf('=')).trim
				if (!(pars exists (p => p.name == key))) throw new GrammarException(s"A parameter in '$text' is not defined")
				val param = pars.find(p => p.name == key).get
				if (param.isList) {
					if (!(value startsWith "[") || !(value endsWith "]")) throw new GrammarException(s"The parameter ${param.name} is a list type, but '$value' is not enclosed in []")
					val zs = value.substring(2, value.length - 1) split "," map (_.trim)
					assert(lengths contains param.name, s"Length is missing for parameter $param")
					if (lengths(param.name) != zs.length) throw new GrammarException(s"The parameter ${param.name} has length ${lengths(param.name)}, but the value '$value' has length ${zs.length}")
					if (!(zs forall (z => pattern.matcher(z).matches))) throw new GrammarException(s"The value '$value' does not match the required pattern ${pattern.pattern} (for parameter ${param.name}")
					DollarSignSub(param, zs.toList, true)
				} else {
					if (!pattern.matcher(value).matches()) throw new GrammarException(s"The value '$value' does not match the required pattern ${pattern.pattern} (for parameter ${param.name}")
					DollarSignSub(param, List(value), false)
				}
			}
		}
	} groupBy (_.key) mapValues (_.head)

}

case class DollarSignSub(key: Param, values: List[String], isList: Boolean)


object DollarSignParams {

	private val paramPattern = """\$\{?(?:\.{3})?[A-Za-z0-9_]*(?:\[[\$A-Za-z0-9_]+\])?\}?""".r

	def substitute(expression: String, substitutions: Map[String, String]) =
		substitutions.foldLeft(expression)((e, s) => e.replaceAllLiterally(s._1, s._2))

	def find(expression: String, predefinedNames: Set[String]): Seq[DollarSignParam] = {
		assert(predefinedNames forall (_.startsWith("$")))
		_find(expression) map (s => DollarSignParam(s, predefinedNames.contains(s) || s.startsWith("$[")))
	}.toSeq

	def _find(expression: String): Iterator[String] = for (m <- paramPattern.findAllMatchIn(expression)) yield m.group(0)

}

trait Param {
	def name: String
}

case class DollarSignParam(override val name: String, isPredefined: Boolean) extends Param {
	assert(name.startsWith("$"), "Parameters must start with a $")
	val isList: Boolean = name contains "..."
	val isArrayAccess: Boolean = name contains "["
	override val toString: String = name
}