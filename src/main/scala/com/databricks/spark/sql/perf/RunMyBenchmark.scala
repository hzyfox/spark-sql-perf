package com.databricks.spark.sql.perf

import java.text.SimpleDateFormat
import java.util.Date

import com.databricks.spark.sql.perf.tpcds.TPCDS
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, substring}

/**
  * create with com.databricks.spark.sql.perf
  * USER: husterfox
  */
case class MyRunConfig(database: String = "tpcds",
                       hive: Boolean = true,
                       resultLocation: String = "",
                       iteration: Int = 1,
                       version: Int = 2,
                       timeout: Int = 24 * 60 * 60
                      )

object RunMyBenchmark {
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[MyRunConfig]("spark-sql-perf MybenchMark") {
      head("spark-sql-perf MyBenchMark Create Table ", "0.2.0")
      opt[String]('d', "database")
        .action((x, c) => c.copy(database = x))
        .text("name of database with TPCDS data.")
      opt[Boolean]('h', "hive")
        .action((x, c) => c.copy(hive = x))
        .text("enable hive support.")
      opt[String]('l', "resloc")
        .action((x, c) => c.copy(resultLocation = x))
        .text("place to write results.")
        .required()
      opt[Int]('i', "iteration")
        .action((x, c) => c.copy(iteration = x))
        .text("how many iterations of queries to run")
      opt[Int]('v', "version")
        .action((x, c) => c.copy(version = x))
        .text("which tpcds queries version to run [1|2]")
      opt[Int]('t', "timeout")
        .action((x, c) => c.copy(timeout = x))
        .text("timeout, in seconds.")
      help("help")
        .text("prints this usage text")
    }

    parser.parse(args, MyRunConfig()) match {
      case Some(config) =>
        run(config)
      case None =>
        parser.showUsage()
        System.exit(1)
    }

  }

  def run(config: MyRunConfig): Unit = {
    val fm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val tim = fm.format(new Date(System.currentTimeMillis()))
    val appName = getClass.getSimpleName + tim
    val spark = if (config.hive) {
      SparkSession.builder().enableHiveSupport().appName(appName).getOrCreate()
    } else {
      SparkSession.builder().appName(appName).getOrCreate()
    }
    val sql = spark.sql _
    val sqlContext = spark.sqlContext
    val tpcds = new TPCDS(sqlContext = sqlContext)
    // Set:
    val databaseName = config.database // name of database with TPCDS data.
    val resultLocation = config.resultLocation // place to write results
    val iterations = config.iteration // how many iterations of queries to run.
    val queries = if (config.version == 2) {
      tpcds.tpcds2_4Queries
    } else {
      tpcds.tpcds1_4Queries
    } // queries to run.

    val timeout = config.timeout // timeout, in seconds.
    // Run:
    sql(s"use $databaseName")
    val experiment = tpcds.runExperiment(
      queries,
      iterations = iterations,
      resultLocation = resultLocation,
      forkThread = true)


    experiment.waitForFinish(timeout)

    import spark.implicits._
    experiment.getCurrentResults // or: spark.read.json(resultLocation).filter("timestamp = 1429132621024")
      .withColumn("Name", substring(col("name"), 2, 100))
      .withColumn("Runtime", (col("parsingTime") + col("analysisTime") + col("optimizationTime") + col("planningTime") + col("executionTime")) / 1000.0)
      .select('Name, 'Runtime).show()


  }


}
