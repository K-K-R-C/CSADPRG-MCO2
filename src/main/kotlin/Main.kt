/*
********************
Last names: Camato, Galicia, Mojica, Orense
Language: Kotlin
Paradigm(s): OOP, Functional Programming, and Imperative Programming
********************
*/

import java.io.File
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

// Assuming you have kotlin-csv library for CSV parsing. Add to your build.gradle:
// implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.0")

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

data class ProjectRow(
    var FundingYear: Int? = null,
    var Region: String = "",
    var MainIsland: String = "",
    var ApprovedBudgetForContract: Double = 0.0,
    var ContractCost: Double = 0.0,
    var StartDate: String = "",
    var ActualCompletionDate: String = "",
    var Contractor: String = "",
    var Province: String = "",
    var TypeOfWork: String = "",
    var CostSavings: Double = 0.0,
    var CompletionDelayDays: Long = 0
)

val dataset = ArrayList<ProjectRow>()
val filteredDataset = ArrayList<ProjectRow>()
var isLoaded = false

// Validate if row contains necessary numeric FundingYear
fun validateRow(row: Map<String, String>): Boolean {
    val fundingYear = row["FundingYear"]
    return fundingYear != null && fundingYear.toDoubleOrNull() != null
}

// Convert FundingYear to number and return processed row
fun processRow(row: Map<String, String>): ProjectRow {
    val project = ProjectRow()
    project.FundingYear = row["FundingYear"]?.toIntOrNull()
    project.Region = row["Region"] ?: ""
    project.MainIsland = row["MainIsland"] ?: ""
    project.ApprovedBudgetForContract = row["ApprovedBudgetForContract"]?.toDoubleOrNull() ?: 0.0
    project.ContractCost = row["ContractCost"]?.toDoubleOrNull() ?: 0.0
    project.StartDate = row["StartDate"] ?: ""
    project.ActualCompletionDate = row["ActualCompletionDate"] ?: ""
    project.Contractor = row["Contractor"] ?: ""
    project.Province = row["Province"] ?: ""
    project.TypeOfWork = row["TypeOfWork"] ?: ""
    return project
}

// Filter data for 2021–2023
fun filterData() {
    filteredDataset.clear()
    filteredDataset.addAll(dataset.filter { it.FundingYear in 2021..2023 })
}

// Load and process file synchronously (assuming small file)
fun loadAndProcessData() {
    dataset.clear()
    filteredDataset.clear()
    isLoaded = false
    try {
        val file = File("dpwh_flood_control_projects.csv")
        csvReader().open(file) {
            // Use readAllWithHeaderAsSequence() to get Map<String, String> rows (headers as keys)
            readAllWithHeaderAsSequence().forEach { row ->
                if (validateRow(row)) {
                    dataset.add(processRow(row))
                }
            }
        }
        filterData()
        println("Processing dataset... (${dataset.size.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")} rows loaded, ${filteredDataset.size.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")} filtered for 2021-2023)\n")
        isLoaded = true
    } catch (e: Exception) {
        println("Error reading file: ${e.message}")
    }
}

fun report1() {
    filterData()
    filteredDataset.forEach { row ->
        row.ApprovedBudgetForContract = row.ApprovedBudgetForContract
        row.ContractCost = row.ContractCost

        // Cost Savings
        row.CostSavings = row.ApprovedBudgetForContract - row.ContractCost

        val start = try { LocalDate.parse(row.StartDate, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { null }
        val end = try { LocalDate.parse(row.ActualCompletionDate, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { null }

        row.CompletionDelayDays = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0
    }

    println("Report 1: Regional Flood Mitigation Efficiency Summary")
    println("Filtered: 2021–2023 Projects\n")

    println("===========================================================================================================================================")
    println(
        " Region".padEnd(33) +
                "|| MainIsland".padEnd(16) +
                "|| TotalBudget".padEnd(22) +
                "|| MedianSavings".padEnd(18) +
                "|| AvgDelay".padEnd(13) +
                "|| HighDelayPct".padEnd(17) +
                "|| EfficiencyScore"
    )
    println("===========================================================================================================================================")

    // Group by Region + MainIsland
    val grouped = HashMap<String, MutableList<ProjectRow>>()
    filteredDataset.forEach { p ->
        val key = "${p.Region}-${p.MainIsland}"
        grouped.getOrPut(key) { ArrayList() }.add(p)
    }

    val summary = grouped.map { (key, projects) ->
        val budgets = projects.map { it.ApprovedBudgetForContract }
        val savings = projects.map { it.CostSavings }
        val delays = projects.map { it.CompletionDelayDays }

        val sortedSavings = savings.sorted()
        val medianSavings = if (sortedSavings.isNotEmpty()) {
            val n = sortedSavings.size
            val mid = n / 2
            if (n % 2 == 1) sortedSavings[mid] else (sortedSavings[mid - 1] + sortedSavings[mid]) / 2
        } else 0.0

        val avgDelay = delays.average()

        // ---- HIGH DELAY PERCENTAGE (>30 days)
        val highDelayCount = delays.count { it > 30 }
        val highDelayPct = (highDelayCount.toDouble() / delays.size) * 100

        var efficiency = 100.0
        if (avgDelay > 0) {
            efficiency = (medianSavings / avgDelay) * 100
            efficiency = min(max(efficiency, 0.0), 100.0)
        }

        mapOf(
            "Region" to projects.first().Region,
            "MainIsland" to projects.first().MainIsland,
            "TotalBudget" to budgets.sum(),
            "MedianSavings" to medianSavings,
            "AverageDelay" to avgDelay,
            "HighDelayPct" to highDelayPct,
            "EfficiencyScore" to efficiency
        )
    }

    // Sort descending by efficiency score
    val sortedSummary = summary.sortedByDescending { (it["EfficiencyScore"] as? Double) ?: 0.0 }

    // Define df here for reuse in both printing and CSV
    val df = DecimalFormat("#,##0.00")

    // Print top 2
    sortedSummary.take(2).forEach { s ->
        println(
            (s["Region"]?.toString() ?: "").padEnd(33) + "|| " +
                    (s["MainIsland"]?.toString() ?: "").padEnd(13) + "|| " +
                    df.format((s["TotalBudget"] as? Double) ?: 0.0).padEnd(18) + " || " +
                    df.format((s["MedianSavings"] as? Double) ?: 0.0).padEnd(14) + " || " +
                    df.format((s["AverageDelay"] as? Double) ?: 0.0).padEnd(9) + " || " +
                    df.format((s["HighDelayPct"] as? Double) ?: 0.0).padEnd(13) + " || " +
                    df.format((s["EfficiencyScore"] as? Double) ?: 0.0).padEnd(16)
        )
    }

    println("\n(Full table exported to report1_regional_summary.csv)")

    val csvHeader = "Region,MainIsland,TotalBudget,MedianSavings,AvgDelay,HighDelayPct,EfficiencyScore\n"
    val csvBody = sortedSummary.joinToString("\n") { s ->
        "\"${s["Region"]?.toString() ?: ""}\",\"${s["MainIsland"]?.toString() ?: ""}\"," +
                "\"${df.format((s["TotalBudget"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["MedianSavings"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["AverageDelay"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["HighDelayPct"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["EfficiencyScore"] as? Double) ?: 0.0)}\""
    }
    try {
        File("report1_regional_summary.csv").writeText(csvHeader + csvBody)
    } catch (e: Exception) {
        println("Error writing CSV file: ${e.message}")
    }
}

fun report2() {
    filterData()
    println("\n\nReport 2: Top Contractors Performance Ranking")
    println("\nTop Contractors Performance Ranking")
    println("Top 15 by TotalCost, >= 5 Projects\n")

    println("===============================================================================================================================================================")
    println(
        " Rank".padEnd(7) +
                "|| Contractor".padEnd(43) +
                "|| TotalCost".padEnd(23) +
                "|| NumProjects".padEnd(16) +
                "|| AvgDelay".padEnd(15) +
                "|| TotalSavings".padEnd(22) +
                "|| ReliabilityIndex".padEnd(13) +
                "|| RiskFlag"
    )
    println("===============================================================================================================================================================")

    // Summarize contractors
    val contractorSummary = HashMap<String, MutableMap<String, Any>>()
    filteredDataset.forEach { row ->
        val contractor = row.Contractor.replace(Regex("\\s*\\(formerly.*?\\)", RegexOption.IGNORE_CASE), "").trim()
            .ifEmpty { "Unknown" }

        val summary = contractorSummary.getOrPut(contractor) {
            mutableMapOf(
                "Contractor" to contractor,
                "TotalCost" to 0.0,
                "NumProjects" to 0,
                "Delays" to ArrayList<Long>(),
                "TotalSavings" to 0.0
            )
        }

        val cost = row.ContractCost
        val approved = row.ApprovedBudgetForContract
        val savings = approved - cost

        val start = try { LocalDate.parse(row.StartDate, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { null }
        val end = try { LocalDate.parse(row.ActualCompletionDate, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { null }
        val delayDays = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0L

        summary["TotalCost"] = (summary["TotalCost"] as Double) + cost
        summary["NumProjects"] = (summary["NumProjects"] as Int) + 1
        (summary["Delays"] as ArrayList<Long>).add(delayDays)
        summary["TotalSavings"] = (summary["TotalSavings"] as Double) + savings
    }

    // Build base summary (no reliability yet)
    var summary = contractorSummary.values.filter { c ->
        (c["NumProjects"] as Int) >= 5 && (c["TotalCost"] as Double) > 0
    }.map { c ->
        val delays = (c["Delays"] as ArrayList<Long>).filter { it > 0 }
        val avgDelay = if (delays.isNotEmpty()) delays.average() else 0.0

        mapOf(
            "Rank" to 0,
            "Contractor" to c["Contractor"],
            "TotalCost" to c["TotalCost"],
            "NumProjects" to c["NumProjects"],
            "AvgDelay" to avgDelay,
            "TotalSavings" to c["TotalSavings"]
        )
    }

    summary = summary.map { s ->
        val avgDelay = s["AvgDelay"] as Double
        val totalSavings = s["TotalSavings"] as Double
        val totalCost = max(s["TotalCost"] as Double, 1.0)

        var reliability = (1 - (avgDelay / 90)) * (totalSavings / totalCost) * 100

        // Keep NaN/Infinity protection
        if (reliability.isNaN() || !reliability.isFinite()) reliability = 0.0

        // Cap upper limit at 100
        if (reliability > 100) reliability = 100.0

        // Convert negative reliability to zero
        if (reliability < 0) reliability = 0.0

        val riskFlag = if (reliability < 50) "High Risk" else "Low Risk"

        s.toMutableMap().apply {
            this["ReliabilityIndex"] = reliability
            this["RiskFlag"] = riskFlag
        }
    }

    // Sort & rank
    val sortedSummary = summary.sortedByDescending { (it["TotalCost"] as? Double) ?: 0.0 }
    sortedSummary.forEachIndexed { idx, s -> s["Rank"] = idx + 1 }

    // Define df here for reuse in both printing and CSV
    val df = DecimalFormat("#,##0.00")

    // Display only top 2 rows
    sortedSummary.take(2).forEach { s ->
        println(
            (s["Rank"]?.toString() ?: "0").padEnd(7) + "|| " +
                    (s["Contractor"]?.toString() ?: "").padEnd(40) + "|| " +
                    df.format((s["TotalCost"] as? Double) ?: 0.0).padEnd(20) + "|| " +
                    (s["NumProjects"]?.toString() ?: "0").padEnd(13) + "|| " +
                    df.format((s["AvgDelay"] as? Double) ?: 0.0).padEnd(12) + "|| " +
                    df.format((s["TotalSavings"] as? Double) ?: 0.0).padEnd(19) + "|| " +
                    df.format((s["ReliabilityIndex"] as? Double) ?: 0.0).padEnd(16) + "|| " +
                    (s["RiskFlag"]?.toString() ?: "").padEnd(10)
        )
    }

    val top15 = sortedSummary.take(15)

    val csvHeader = "Rank,Contractor,TotalCost,NumProjects,AvgDelay,TotalSavings,ReliabilityIndex,RiskFlag\n"
    val csvBody = top15.joinToString("\n") { s ->
        "\"${s["Rank"]?.toString() ?: "0"}\"," +
                "\"${s["Contractor"]?.toString() ?: ""}\"," +
                "\"${df.format((s["TotalCost"] as? Double) ?: 0.0)}\"," +
                "\"${s["NumProjects"]?.toString() ?: "0"}\"," +
                "\"${df.format((s["AvgDelay"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["TotalSavings"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((s["ReliabilityIndex"] as? Double) ?: 0.0)}\"," +
                "\"${s["RiskFlag"]?.toString() ?: ""}\""
    }

    try {
        File("report2_contractor_ranking.csv").writeText(csvHeader + csvBody)
    } catch (e: Exception) {
        println("Error writing CSV file: ${e.message}")
    }
    println("\n(Full table exported to report2_contractor_ranking.csv)")
}


fun report3() {
    filterData()
    println("\n\nReport 3: Annual Project Type Cost Overrun Trends")
    println("\nAnnual Project Type Cost Overrun Trends")
    println("Grouped by FundingYear and TypeOfWork\n")

    println("============================================================================================================================================")
    println(
        " FundingYear".padEnd(14) +
                "|| TypeOfWork".padEnd(55) +
                "|| TotalProjects".padEnd(20) +
                "|| AvgSavings".padEnd(20) +
                "|| OverrunRate".padEnd(15) +
                "|| YoYChange"
    )
    println("============================================================================================================================================")

    // Group by FundingYear + TypeOfWork
    val summary = HashMap<String, MutableMap<String, Any>>()

    filteredDataset.forEach { row ->
        val year = row.FundingYear ?: 0
        val type = row.TypeOfWork.trim().ifEmpty { "Unknown" }
        val key = "$year||$type"

        val cost = row.ContractCost
        val approved = row.ApprovedBudgetForContract
        val savings = approved - cost // negative = overrun

        val group = summary.getOrPut(key) {
            mutableMapOf(
                "FundingYear" to year,
                "TypeOfWork" to type,
                "totalProjects" to 0,
                "totalSavings" to 0.0,
                "overrunCount" to 0
            )
        }

        group["totalProjects"] = (group["totalProjects"] as Int) + 1
        group["totalSavings"] = (group["totalSavings"] as Double) + savings
        if (savings < 0) group["overrunCount"] = (group["overrunCount"] as Int) + 1
    }

    // Compute per-group averages and rates
    val results = summary.values.map { g ->
        val avgSavings = (g["totalSavings"] as Double) / (g["totalProjects"] as Int)
        val overrunRate = ((g["overrunCount"] as Int).toDouble() / (g["totalProjects"] as Int)) * 100
        mutableMapOf(  // Changed to mutableMapOf for mutability
            "FundingYear" to g["FundingYear"],
            "TypeOfWork" to g["TypeOfWork"],
            "TotalProjects" to g["totalProjects"],
            "AvgSavings" to avgSavings,
            "OverrunRate" to overrunRate,
            "YoYChange" to 0.00 // default baseline
        )
    }.toMutableList()

    // Compute YoY % change (baseline = 2021)
    val baseline = HashMap<String, Double>()
    results.forEach { r ->
        if (r["FundingYear"] == 2021) baseline[r["TypeOfWork"] as String] = r["AvgSavings"] as Double
    }

    results.forEach { r ->
        val type = r["TypeOfWork"] as String
        val year = r["FundingYear"] as Int
        if (year != 2021 && baseline.containsKey(type) && baseline[type] != 0.0) {
            val change = ((r["AvgSavings"] as Double - baseline[type]!!) / kotlin.math.abs(baseline[type]!!)) * 100
            r["YoYChange"] = String.format("%.2f", change).toDouble()  // Now works since r is mutable
        }
    }

    // Sort ascending by year, descending by AvgSavings
    results.sortWith(compareBy({ (it["FundingYear"] as? Int) ?: 0 }, { -((it["AvgSavings"] as? Double) ?: 0.0) }))

    // Define df here for reuse in both printing and CSV
    val df = DecimalFormat("#,##0.00")

    //  Display only top 3 rows on screen
    results.take(3).forEach { r ->
        println(
            (r["FundingYear"]?.toString() ?: "0").padEnd(14) + "|| " +
                    (r["TypeOfWork"]?.toString() ?: "").padEnd(52) + "|| " +
                    (r["TotalProjects"]?.toString() ?: "0").padEnd(17) + "|| " +
                    df.format((r["AvgSavings"] as? Double) ?: 0.0).padEnd(17) + "|| " +
                    df.format((r["OverrunRate"] as? Double) ?: 0.0).padEnd(12) + "|| " +
                    df.format((r["YoYChange"] as? Double) ?: 0.0).padEnd(10)
        )
    }

    // Export full results to CSV
    val csvHeader = "FundingYear,TypeOfWork,TotalProjects,AvgSavings,OverrunRate(%),YoYChange(%)\n"
    val csvRows = results.joinToString("\n") { r ->
        "\"${r["FundingYear"]?.toString() ?: "0"}\"," +
                "\"${r["TypeOfWork"]?.toString() ?: ""}\"," +
                "\"${r["TotalProjects"]?.toString() ?: "0"}\"," +
                "\"${df.format((r["AvgSavings"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((r["OverrunRate"] as? Double) ?: 0.0)}\"," +
                "\"${df.format((r["YoYChange"] as? Double) ?: 0.0)}\""
    }
    try {
        File("report3_annual_trends.csv").writeText(csvHeader + csvRows)
    } catch (e: Exception) {
        println("Error writing CSV file: ${e.message}")
    }
    println("\n(Full table exported to report3_annual_trends.csv)")
}



fun summaryStats() {
    filterData()

    // Total projects
    val totalProjects = filteredDataset.size

    // Total contractors
    val contractorsSet = HashSet<String>()
    filteredDataset.forEach { r ->
        val contractor = r.Contractor.replace(Regex("\\s*\\\$formerly.*?\\\$", RegexOption.IGNORE_CASE), "").trim()
            .ifEmpty { "Unknown" }
        contractorsSet.add(contractor)
    }
    val totalContractors = contractorsSet.size

    // Total provinces/regions with projects
    val provincesSet = HashSet<String>()
    filteredDataset.forEach { r ->
        provincesSet.add(r.Province.ifEmpty { "Unknown" })
    }
    val totalProvinces = provincesSet.size

    // Global average delay
    val delays = filteredDataset.map { it.CompletionDelayDays }.filter { it > 0 }
    val globalAvgDelay = if (delays.isNotEmpty()) delays.average() else 0.0

    // Total savings
    val totalSavings = filteredDataset.sumOf { r ->
        val approved = r.ApprovedBudgetForContract
        val cost = r.ContractCost
        approved - cost
    }

    // Formatting helper
    val fmt = { num: Double ->
        DecimalFormat("#,##0.00").format(num)
    }

    // Build formatted summary object (strings with commas)
    val summaryFormatted = mapOf(
        "totalProjects" to totalProjects.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "\$1,"),
        "totalContractors" to totalContractors.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "\$1,"),
        "totalProvinces" to totalProvinces.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "\$1,"),
        "globalAvgDelay" to fmt(globalAvgDelay),
        "totalSavings" to fmt(totalSavings)
    )

    // Serializer for Map<String, String>
    val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    println("\n\nSummary Stats (summary.json):")
    val yellow = "\u001B[33m"
    val reset = "\u001B[0m"
    println("$yellow${kotlinx.serialization.json.Json.encodeToString(mapSerializer, summaryFormatted)}$reset")
    println("\n")
    File("summary.json").writeText(kotlinx.serialization.json.Json.encodeToString(mapSerializer, summaryFormatted))
}



fun generateReports() {
    report1()
    report2()
    report3()
    summaryStats()
}

// Main Menu Function
fun mainMenu() {
    val scanner = Scanner(System.`in`)
    var choice: String

    do {
        println("\nSelect Language Implementation:")
        println("[1] Load the File")
        println("[2] Generate Reports")
        println("[3] Exit")

        print("\nEnter choice: ")
        choice = scanner.nextLine().trim()

        when (choice) {
            "1" -> {
                loadAndProcessData()
                mainMenu()
                return
            }
            "2" -> {
                if (!isLoaded || filteredDataset.isEmpty()) {
                    println("\nNo file loaded yet. Load a file first.\n")
                } else {
                    var flag = true
                    do {
                        println("\nGenerating Reports...")
                        println("Outputs saved to individual files.\n")
                        generateReports()
                        var stayInReports: String
                        do {
                            print("Back to Report Selection (Y/N): ")
                            stayInReports = scanner.nextLine().trim()
                            when (stayInReports.lowercase()) {
                                "y" -> {
                                    flag = true
                                    break
                                }
                                "n" -> {
                                    flag = false
                                    break
                                }
                                else -> {
                                    println("\nInvalid input, please try again.\n")
                                }
                            }
                        } while (true)
                    } while (flag)
                }
            }
            "3" -> {
                println("\nGoodbye!")
                return
            }
            else -> {
                println("\nInvalid input, please try again.\n")
            }
        }
    } while (choice != "3")
}

fun main() {
    mainMenu()
}
