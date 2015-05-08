//package com.balagez.testing
package com.andreasschuh.repeat.core

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

/**
 * Provides methods to measure and report the time used to execute Scala code.
 * Inspired by the [[http://www.ruby-doc.org/stdlib-1.9.2/libdoc/benchmark/rdoc/Benchmark.html Ruby Benchmark library]].
 * 
 * '''Example - Measurements'''
 * 
 * Measuring execution time to construct a 10M long string of letter "a". Note that parenthesis
 * can be omitted which helps readability. This outputs the measurement below (run on a Core i7 Macbook Pro).
 * 
 * {{{
 * println(Benchmark.measure { "a" * 10485760 })
 * }}}
 * {{{
 *     0.175734    0.000568    0.176302    0.215648
 * }}}
 * 
 * This report shows, from left to right, the CPU time spent in user mode, the CPU time spent in
 * system mode, the total CPU time (user mode or system mode) and real time in seconds.
 * 
 * '''Example - Reports'''
 * 
 * Reports are measurements which belong to each other. Reports provide extra functionality like adding
 * report headers and labels to each measurements.
 * 
 * {{{
 * Benchmark.bm { x =>
 *   x.caption("Benchmarking the speed of creating a 10M long string of \"a\"")
 *   x.report { "a" * 10485760 }
 * }
 * }}}
 * {{{
 * Benchmarking the speed of creating a 10M long string of "a"
 *         user      system       total        real
 *     0.134755    0.000248    0.135003    0.168390
 * }}}
 * 
 * '''Example - Advanced reports'''
 * 
 * This example is taken form `BenchmarkExample` which calculated the 30th Fibonacci number using various
 * algorithms.
 * 
 * {{{
 * val n = 30
 * Benchmark.bm(20, { x =>
 *   x.caption("Calculating Fibonacci(%d) using different algorithms:".format(n))
 *   x.report("Recursive:", { FibonacciRecursive.fib(n) })
 *   x.report("Iterative:", { FibonacciIterative.fib(n) })
 *   x.report("Tail recursive:", { FibonacciTailRecusive.fib(n) })
 *   x.report("Functional stream:", { FibonacciFunctionalStream.fib(n) })
 *   x.report("Fold left:", { FibonacciFoldLeft.fib(n) })
 *   x.separator
 *   x.total()
 *   x.avg()
 * })
 * }}}
 * {{{
 * Calculating Fibonacci(30) using different algorithms:
 *                             user      system       total        real
 * Recursive:              0.010270    0.000185    0.010455    0.010713
 * Iterative:              0.000441    0.000065    0.000506    0.000505
 * Tail recursive:         0.000009    0.000004    0.000013    0.000011
 * Functional stream:      0.000685    0.000110    0.000795    0.000805
 * Fold left:              0.000696    0.000112    0.000808    0.000817
 * --------------------------------------------------------------------
 * Total:                  0.012101    0.000476    0.012577    0.012851
 * Avg:                    0.002420    0.000095    0.002515    0.002570
 * }}}
 * 
 * @param labelWidth Sets the width of prepended labels. Default is 0 which turns off labels.
 * @author Zoltán Balázs
 * @version 0.1
 */
class Benchmark(val labelWidth: Int = 0) {
	
	/**
	 * Stores timestamps which describe a state of the current thread.
	 * @param ctime CPU time consumed by the current thread, equals the time spent in user mode or system mode, in nanoseconds.
	 * @param utime Time spent in user mode of the current thread, in nanoseconds.
	 * @param real Current real time in nanoseconds.
	 */
	case class Tms(ctime: Long = 0, utime: Long = 0, real: Long = 0) {
		
		/**
		 * Calculates elapsed time since the time stored in this Tms object has been recorded.
		 */
		def elapsed = {
			val o = Tms()
			new Tms(o.ctime - ctime, o.utime - utime, o.real - real)
		}
		
		/**
		 * Returns a Map of times divided by the operand.
		 * @param o All times in this object will be divided by this number.
		 */
		private def \(o: Long) = Map(
			"user" -> utime.toDouble / o,
			"system" -> (ctime - utime).toDouble / o,
			"total" -> ctime.toDouble / o,
			"real" -> real.toDouble / o
		)
		
		/**
		 * Addition operator.
		 * @param o Addition operand.
		 */
		def +(o: Tms) = new Tms(ctime + o.ctime, utime + o.utime, real + o.real)
		
		/**
		 * Division operator.
		 * @param o Division operand.
		 */
		def /(o: Long) = new Tms(ctime / o, utime / o, real / o)
		
		/**
		 * Times as a String.
		 */
		override def toString = "%12.6f%12.6f%12.6f%12.6f".format(s("user"), s("system"), s("total"), s("real"))
		
		/**
		 * Recorded times as a Map of seconds.
		 */
		lazy val s = this \ 1000000000
		
		/**
		 * Recorded times as a map of milliseconds.
		 */
		lazy val ms = this \ 1000000
		
		/**
		 * Recorded times as a map of microseconds.
		 */
		lazy val us = this \ 1000
		
		/**
		 * Recorded times as a map of nanoseconds.
		 */
		lazy val ns = this \ 1
	}
	
	/**
	 * Provides factory for a Tms object initialized with current CPU, user and real time.
	 * Usage: val time = Tms()
	 */
	private object Tms {
		lazy val threadMXBean = ManagementFactory.getThreadMXBean().asInstanceOf[ThreadMXBean]
		
		def apply() = new Tms(threadMXBean.getCurrentThreadCpuTime(), threadMXBean.getCurrentThreadUserTime(), System.nanoTime())
	}

	/**
	 * Prints header only once for this benchmark.
	 */
	private lazy val header = println(" " * labelWidth + "        user      system       total        real")
	
	/**
	 * Format string for report labels.
	 */
	private lazy val labelFormatter = labelWidth match {
		case 0 => ""
		case _ => "%1$-" + labelWidth + "s"
	}
	
	/**
	 * Collects measurements for totals.
	 */
	private val reports: collection.mutable.ListBuffer[Tms] = collection.mutable.ListBuffer()
	
	/**
	 * Prints a caption text above benchmark report.
	 * @param caption Benchmark caption
	 */
	def caption(caption: String) = println(caption)
	
	/**
	 * Measures execution time of a code block and returns measurements.
	 * @param code Code block to be benchmarked.
	 * @return Execution time of the code block.
	 */
	def measure(code: => Unit) = {
		val startTime = Tms()
		code
		startTime.elapsed
	}
	
	/**
	 * Measures execution time of a code block and prints formatted results.
	 * @param code Code block to be benchmarked.
	 */
	def report(code: => Unit): Unit = report("", code)
	
	/**
	 * Measures execution time of a code block and prints formatted results with prepended label.
	 * @param label Label to be prepended to the report line.
	 * @param code Code block to be benchmarked.
	 */
	def report(label: String, code: => Unit) = {
		reports += measure(code)
		header
		println(labelFormatter.format(label) + reports.last)
	}
	
	/**
	 * Prints a horizontal line.
	 */
	def separator = println("-" * (labelWidth + 48))
	
	/**
	 * Prints total of execution times.
	 * @param label Label to be prepended to the report line.
	 */
	def total(label: String = "Total:") = {
		println(labelFormatter.format(label) + reports.foldLeft(new Tms())( _ + _ ))
	}
	
	/**
	 * Prints average of execution times.
	 * @param label Label to be prepended to the report line.
	 */
	def avg(label: String = "Avg:") = {
		println(labelFormatter.format(label) + reports.foldLeft(new Tms())( _ + _ ) / reports.length)
	}
}

/**
 * Companion object for Benchmark class.
 */
object Benchmark {
	
	/**
	 * Factory method for Benchmark class.
	 * @param what Code block in which the Benchmark object will be alive.
	 */
	def bm(what: Benchmark => Unit) = {
		val bm = new Benchmark
		what(bm)
	}
	
	/**
	 * Factory method for Benchmark class.
	 * @param what Code block in which the Benchmark object will be alive.
	 */
	def bm(labelWidth: Int, what: Benchmark => Unit) = {
		val bm = new Benchmark(labelWidth)
		what(bm)
	}
	
	/**
	 * Measures execution time of a code block and returns measurements.
	 * @param code Code block to be benchmarked.
	 * @return Execution time of the code block.
	 */
	def measure(code: => Unit) = {
		val bm = new Benchmark
		bm.measure(code)
	}
}