package com.ft.aio.template.adapter.output.web.script.engine.Alns

import com.ft.aio.template.adapter.output.web.scrippt.staff.Staff
import com.ft.aio.template.adapter.output.web.scrippt.input.InputData
import com.ft.aio.template.adapter.output.web.scrippt.shift.Shift
import com.ft.aio.template.adapter.output.web.scrippt.staff.StaffGroup
import java.nio.DoubleBuffer
import kotlin.math.abs
import kotlin.math.max

// Adaptive large neighborhood search
// **@author: Dat Le

import kotlin.random.Random
import kotlin.math.exp

open class Alns(val data: InputData) {
    var numberIterations: Int = 1000
    var temperature: Double = 100.0
    var alpha: Double = 0.9
    var limit: Double = 1e-3
    var deltaE: Double = 0.0
    var score: Double = 0.0
    var penalty: Double = Int.MAX_VALUE.toDouble()
    var probabilitiesOfOperator: MutableMap<Int, Double> = mutableMapOf()
    var operatorScore: MutableMap<Int, Double> = mutableMapOf()
    var operatorWeight: MutableMap<Int, Double> = mutableMapOf()
    var operatorTimes: MutableMap<Int, Double> = mutableMapOf()
    var solution: MutableMap<String, MutableMap<Int, String>> = mutableMapOf()

    private fun caculateScore(schedules: MutableMap<String, MutableMap<Int, String>>): Double {
        var scores = 0

        // coverage
        for (coverage in data.coverages) {
            if (coverage.type.find { it == "hard" } != null ) {
                if(coverage.type.find { it == "equal to" } != null) {
                    scores += abs(
                        (caculateCoverageFullfillment(
                            schedules,
                            coverage.id,
                            coverage.day
                        ) - coverage.desireValue)
                    ) * coverage.penalty
                }
            }
            else if(coverage.type.find { it == "soft" } != null){
                if(coverage.type.find { it == "at least" } != null) {
                    scores += max(
                        (coverage.desireValue - caculateCoverageFullfillment(
                            schedules,
                            coverage.id,
                            coverage.day
                        )) * coverage.penalty, 0
                    )
                }
            }
        }

        // horizontal coverage
        for (coverage in data.horizontalCoverages){
            var horizontalCoverage = caculateHorizontalCoverageFullfillment(schedules, coverage.id)
            if (coverage.type.find { it == "hard" } != null ) {
                if(coverage.type.find { it == "equal to" } != null) {
                    for (map in horizontalCoverage) {
                        scores += abs(coverage.desireValue - map.value) * coverage.penalty
                    }
                }
            }
            else if(coverage.type.find { it == "soft" } != null){
                if(coverage.type.find { it == "at least" } != null){
                    for (map in horizontalCoverage){
                        scores += max(0, map.value - coverage.desireValue)*coverage.penalty
                    }
                }
            }
        }

        this.score = Int.MAX_VALUE.toDouble()- scores
        return this.score.toDouble()
    }

    private fun createWeightOperators() {
        for (index in 0..2) {
            if (this.operatorWeight.get(index) != null){
                this.operatorWeight.set(
                    index,
                    this.operatorWeight.get(index)!! * 0.2 + 0.8 * this.operatorScore?.get(index)!! / this.operatorTimes?.get(index)!!)
            }
            else{
                this.operatorWeight.set(
                    index,
                    0.2 + 0.8 * this.operatorScore?.get(index)!! / this.operatorTimes?.get(index)!!)
            }
        }
    }

    private fun createScoreOperator() {
        this.operatorScore.set(0, 1.0)
        this.operatorScore.set(1, 0.5)
        this.operatorScore.set(2, 0.5)
    }

    private fun createOperatorTimes() {
        for (index in 0..2) {
            this.operatorTimes.set(index, 1.0)
        }
    }

    private fun caculateSimulatedAnealing(
        currentScheduled: MutableMap<String, MutableMap<Int, String>>,
        nextScheduled:MutableMap<String, MutableMap<Int, String>>
    ): MutableMap<String, MutableMap<Int, String>> {
        deltaE = caculateScore(currentScheduled)- caculateScore(nextScheduled)
        if (deltaE < 0){
            return nextScheduled
        }
        else {
            if (temperature < limit) {
                return currentScheduled
            }
            val probability = exp(deltaE / temperature)
            val acceptanceVariable = Random.nextDouble(0.0, 1.0)

            temperature *= alpha
            if (probability > acceptanceVariable) {
                return currentScheduled
            } else {
                return nextScheduled
            }
        }
    }

    private fun getShiftInfoFromCoverage(coverageId: String): String {
        return coverageId.take(2)
    }

    private fun checkIfStaffInStaffGroup(
        staff: Staff,
        staffGroups: List<String>
    ): Boolean {
        var result = false
        for (staffGroupId in staffGroups) {
            val staffGroup = data.staffGroups.find { it.id == staffGroupId }

            if (staffGroup != null) {
                for (staffInfo in staffGroup.staffList) {
                    if (staff.id == staffInfo) {
                        result = true
                        break
                    }
                }
            }

            if (result) {
                break
            }
        }
        return result
    }

    private fun caculateCoverageFullfillment(
        schedules: MutableMap<String, MutableMap<Int, String>>,
        coverageId: String, dayId: Int
    ): Int {
        val coverage = data.coverages.find { it.id == coverageId && it.day == dayId }
        var temp = 0
        if (coverage != null){
            for (staff in data.staffs){
                if (schedules[staff.id]?.get(dayId) == getShiftInfoFromCoverage(coverageId) && checkIfStaffInStaffGroup(staff, coverage.staffGroups)) {
                    temp += 1
                }
            }
        }

        return temp
    }

    private fun caculateHorizontalCoverageFullfillment(
        schedules: MutableMap<String, MutableMap<Int, String>>,
        coverageId: Int
    ): MutableMap<String, Int> {
        var temp2: MutableMap<String, Int> = mutableMapOf()
        val coverage = data.horizontalCoverages.find { it.id == coverageId }
        for (staff in data.staffs){
            temp2.set(staff.id, 0)
            for (day in 1..7) {
                if (coverage != null) {
                    if (schedules[staff.id]?.get(day)!! in coverage.shifts && day in coverage.days) {
                        temp2.set(staff.id, temp2.get(staff.id)!! +1 )
                    }
                }
            }
        }
        return temp2
    }

    private fun inititalSolution(): MutableMap<String, MutableMap<Int, String>> {
        var schedule : MutableMap<String, MutableMap<Int, String>>
        schedule = mutableMapOf()

        // create blank schedule for caculating
        for (staff in data.staffs) {
            schedule[staff.id] = mutableMapOf()
            for (day in 1..7){
                schedule[staff.id]?.set(day, "")
            }
        }

        for (coverage in data.coverages) {
            for (staff in data.staffs) {
                if(caculateCoverageFullfillment(schedule, coverage.id, coverage.day) < coverage.desireValue &&
                    checkIfStaffInStaffGroup(staff, coverage.staffGroups) &&
                    checkIfStaffInStaffGroup(staff, coverage.staffGroups) &&
                    schedule[staff.id]?.get(coverage.day) == ""){
                    schedule[staff.id]?.set(coverage.day, coverage.shift.random())
                }
            }
        }
        for (coverage in data.coverages){
            for (staff in data.staffs){
                if(schedule[staff.id]?.get(coverage.day) == ""){
                    schedule[staff.id]?.set(coverage.day, data.shifts.random().id)
                }
            }
        }

        return schedule
    }

    private fun randomDestroySolution(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>> {
        val mutableSchedule = schedules.toMutableMap()
        if (mutableSchedule.isNotEmpty()) {

            val randomScheduleStaff = mutableSchedule.keys.random()
            val randomScheduleDay = mutableSchedule[randomScheduleStaff]?.keys?.random()
            if (randomScheduleDay != null) {
                mutableSchedule[randomScheduleStaff]?.set(randomScheduleDay.toInt(), "")
            }
        }
        return mutableSchedule
    }

    private fun repairSolution(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>> {
        var repairedSchedule = schedules.toMutableMap()

        for (staffId in repairedSchedule.keys) {
            for (dayId in repairedSchedule[staffId]!!.keys) {
                if (repairedSchedule[staffId]?.get(dayId) == ""){
                    repairedSchedule[staffId]?.set(dayId, data.shifts.random().id)
                }
            }
        }
        return repairedSchedule
    }

    private fun randomSwapStaffShift(currentSolution: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>> {
        var newSolution = randomDestroySolution(currentSolution)
        newSolution = repairSolution(newSolution)
        return newSolution
    }

    private fun greedyCoverageEnhancement(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>>{

        //TODO
        return schedules
    }

    private fun greedyCoverageHorizontalEnhancement(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>>{

        //TODO
        return schedules
    }

    private fun routewheel(): Int{
        createWeightOperators()

        var rand = Random.nextDouble()
        var S = 0.0

        for (index in 0 until this.operatorWeight.size){
            S += this.operatorWeight.get(index)!!
        }
        this.probabilitiesOfOperator.set(0 , this.operatorWeight.get(0)!!/S)
        for (index in 1 until this.operatorWeight.size){
            this.probabilitiesOfOperator.set(index, this.probabilitiesOfOperator.get(index - 1)!! + this.operatorWeight.get(index)!!/S)
        }

        var choseValue = 0

        if (rand <= this.probabilitiesOfOperator.get(0)!!){
            choseValue = 0
        }
        else{
            for(index in 1 until this.operatorScore.size ){
                if (rand > this.probabilitiesOfOperator.get(index - 1)!! && rand <= this.probabilitiesOfOperator.get(index)!!){
                    choseValue = index
                }
            }
        }
        this.operatorTimes.set(choseValue, this.operatorTimes.get(choseValue)!! + 1)
        return choseValue
    }

    private fun shakeAndRepair(schedules: MutableMap<String, MutableMap<Int, String>>, number: Int): MutableMap<String, MutableMap<Int, String>>{
        when (number){
            0 ->{
                return randomSwapStaffShift(schedules)
            }
            1 ->{
                return greedyCoverageEnhancement(schedules)
            }
            2 -> {
                return greedyCoverageHorizontalEnhancement(schedules)
            }
        }
        return schedules
    }

    open fun runAlns(){
        var initialSolution = inititalSolution()
        var currentSolution = initialSolution
        createScoreOperator()
        createOperatorTimes()

        for (i in 1..this.numberIterations) {
            val operatorIndex = routewheel()
            var nextSolution = shakeAndRepair(currentSolution, operatorIndex)
            currentSolution = caculateSimulatedAnealing(currentSolution, nextSolution)
        }

        this.solution = currentSolution
        this.score = caculateScore(this.solution)
        this.penalty = this.penalty - this.score
    }
}