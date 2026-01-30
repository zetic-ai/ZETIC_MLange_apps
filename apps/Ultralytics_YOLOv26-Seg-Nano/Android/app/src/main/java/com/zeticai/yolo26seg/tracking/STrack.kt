package com.zeticai.yolo26seg.tracking

import android.graphics.RectF

class STrack(var tlwh: FloatArray, val score: Float, val classId: Int) {
    var trackId = 0
    var state = TrackState.New
    var isActivated = false
    var frameId = 0
    var trackletLen = 0
    var startFrame = 0

    // Kalman State
    var mean: FloatArray? = null
    var covariance: Array<FloatArray>? = null
    
    // Mask Logic
    var maskCoeffs: FloatArray? = null
    var mask: android.graphics.Bitmap? = null

    val tlbr: FloatArray
        get() = floatArrayOf(tlwh[0], tlwh[1], tlwh[0] + tlwh[2], tlwh[1] + tlwh[3])

    companion object {
        fun nextId(): Int {
            count++
            return count
        }
        private var count = 0
    }

    fun activate(frameId: Int, mask: android.graphics.Bitmap?) {
        this.trackId = nextId()
        val kf = KalmanFilter()
        val xyah = toXyah(tlwh)
        val (m, c) = kf.initiate(xyah)
        this.mean = m
        this.covariance = c
        this.trackletLen = 0
        this.state = TrackState.Tracked
        if (frameId == 1) {
            this.isActivated = true
        }
        this.frameId = frameId
        this.startFrame = frameId
        this.mask = mask
    }

    fun reActivate(newTrack: STrack, frameId: Int, newId: Boolean = false) {
        val kf = KalmanFilter()
        val xyah = toXyah(newTrack.tlwh)
        val (m, c) = kf.update(this.mean!!, this.covariance!!, xyah)
        this.mean = m
        this.covariance = c
        this.tlwh = newTrack.tlwh
        this.maskCoeffs = newTrack.maskCoeffs
        this.mask = newTrack.mask // Update mask
        this.trackletLen = 0
        this.state = TrackState.Tracked
        this.isActivated = true
        this.frameId = frameId
        if (newId) this.trackId = nextId()
    }

    fun update(newTrack: STrack, frameId: Int) {
        val kf = KalmanFilter()
        val xyah = toXyah(newTrack.tlwh)
        val (m, c) = kf.update(this.mean!!, this.covariance!!, xyah)
        this.mean = m
        this.covariance = c
        this.tlwh = newTrack.tlwh
        this.maskCoeffs = newTrack.maskCoeffs
        this.mask = newTrack.mask // Update mask
        this.trackletLen++
        this.state = TrackState.Tracked
        this.isActivated = true
        this.frameId = frameId
    }

    fun markLost() {
        this.state = TrackState.Lost
    }

    fun markRemoved() {
        this.state = TrackState.Removed
    }

    private fun toXyah(tlwh: FloatArray): FloatArray {
        // tlwh -> x, y, aspect_ratio, h
        val x = tlwh[0] + tlwh[2] / 2
        val y = tlwh[1] + tlwh[3] / 2
        val h = tlwh[3]
        val a = tlwh[2] / h
        return floatArrayOf(x, y, a, h)
    }
    
    // Convert current mean state to TLWH
    fun toTlwh(): FloatArray {
        if (mean == null) return tlwh
        val x = mean!![0]
        val y = mean!![1]
        val a = mean!![2]
        val h = mean!![3]
        val w = a * h
        return floatArrayOf(x - w / 2, y - h / 2, w, h)
    }
    
    // Convert to RectF
    fun getRectF(): RectF {
        val t = toTlwh()
        return RectF(t[0], t[1], t[0] + t[2], t[1] + t[3])
    }
}

enum class TrackState {
    New, Tracked, Lost, Removed
}
