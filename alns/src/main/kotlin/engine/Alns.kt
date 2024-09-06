package com.ft.aio.template.adapter.output.web.script.engine.Alns

import com.ft.aio.template.adapter.output.web.scrippt.staff.Staff
import com.ft.aio.template.adapter.output.web.scrippt.input.InputData
import com.ft.aio.template.adapter.output.web.scrippt.shift.Shift
import com.ft.aio.template.adapter.output.web.scrippt.staff.StaffGroup

import kotlin.random.Random
import kotlin.math.exp

open class Alns(val data: InputData) {
    var numberIterations: Int = 100
    var temperature: Double = 100.0
    var alpha: Double = 0.9
    var limit: Double = 1e-3
    var deltaE: Double = 0.0

    open fun caculateScore(schedules: MutableMap<String, MutableMap<Int, String>>): Double{
        var score: Int = Int.MAX_VALUE

        for (coverage in data.coverages) {
            score -= caculateCoverageFulllillment(schedules, coverage.id, coverage.day)*coverage.penalty
        }
        return score.toDouble()
    }

    open fun caculateSimulatedAnealing(currentScheduled: MutableMap<String, MutableMap<Int, String>>, nextScheduled:MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>>{
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

            temperature = temperature * alpha
            if (probability > acceptanceVariable) {
                return currentScheduled
            } else {
                return nextScheduled
            }
        }
    }

    fun getShiftInfoFromCoverage(coverageId: String): String{
        return coverageId.take(2)
    }

    fun checkIfStaffInStaffGroup(staff: Staff, staffGroups: List<String>): Boolean{
        var result: Boolean = false
        for (staffGroupId in staffGroups) {
            for (staffInfo in data.staffsGroup.find{ it.id == staffGroupId }?.staffList!!) {
                if (staff.id == staffInfo.id) {
                    result == true
                }
            }
        }
        return result
    }

    fun caculateCoverageFulllillment(schedules: MutableMap<String, MutableMap<Int, String>>, coverageId: String, dayId:Int): Int{
        val coverage = data.coverages.find { it.id == coverageId && it.day == dayId }
        var temp =0
        if (coverage != null){
            for (staff in data.staffs){
                if (schedules[staff.id]?.get(dayId) == getShiftInfoFromCoverage(coverageId) && checkIfStaffInStaffGroup(staff, coverage.staffGroup)) {
                    temp += 1
                }
            }
        }

        return temp
    }

    open fun inititalSolution(): MutableMap<String, MutableMap<Int, String>>{
        val schedule : MutableMap<String, MutableMap<Int, String>>
        schedule = mutableMapOf()

        // create blank schedule for caculating
        for (staff in data.staffs) {
            schedule[staff.id] = mutableMapOf()
            for (day in 1..7){
                schedule[staff.id]?.set(day, "")
            }
        }
        var temp = 0

        for (coverage in data.coverages) {
            for (staff in data.staffs) {
                if(caculateCoverageFulllillment(schedule, coverage.id, coverage.day) < coverage.desireValue &&
                    checkIfStaffInStaffGroup(staff, coverage.staffGroup) &&
                    checkIfStaffInStaffGroup(staff, coverage.staffGroup) &&
                    schedule[staff.id]?.get(coverage.day) == ""){
                    schedule[staff.id]?.set(coverage.day, coverage.shifts.random())
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

    open fun destroySolution(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>> {
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

    open fun repairSolution(schedules: MutableMap<String, MutableMap<Int, String>>): MutableMap<String, MutableMap<Int, String>> {
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

    open fun runAlns(): MutableMap<String, MutableMap<Int, String>>{
        var initialSolution = inititalSolution()
        for (item in initialSolution){
            println(item.value)
        }
        var currentSolution = initialSolution
        try {
            for (i in 1..numberIterations) {
                var tempSolution = currentSolution
                currentSolution = destroySolution(currentSolution)
                currentSolution = repairSolution(currentSolution)
                currentSolution = caculateSimulatedAnealing(tempSolution, currentSolution)
            }
        }
        catch (e: Exception){}

        return currentSolution
    }
}
