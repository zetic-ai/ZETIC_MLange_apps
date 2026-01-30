package com.zeticai.yolo26seg.tracking

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class ByteTracker(
    private val trackThresh: Float = 0.5f,
    private val trackBuffer: Int = 30,
    private val matchThresh: Float = 0.8f,
    private val frameRate: Int = 30
) {
    private var frameId = 0
    private val trackedStracks = ArrayList<STrack>()
    private val lostStracks = ArrayList<STrack>()
    private val removedStracks = ArrayList<STrack>()
    
    private val kalmanFilter = KalmanFilter()

    fun update(detections: List<STrack>): List<STrack> {
        frameId++
        
        // 1. Activate new tracks
        val activatedStarcks = ArrayList<STrack>()
        val refindStracks = ArrayList<STrack>()
        
        // CRITICAL FIX: Prevent infinite growth of removed tracks
        removedStracks.clear() 

        val detectionsHigh = ArrayList<STrack>()
        val detectionsLow = ArrayList<STrack>()

        for (det in detections) {
            if (det.score >= trackThresh) {
                detectionsHigh.add(det)
            } else {
                detectionsLow.add(det)
            }
        }

        // 1. Predict
        val unconfirmed = ArrayList<STrack>()
        val tracked = ArrayList<STrack>()
        val strackPool = ArrayList<STrack>()

        for (strack in trackedStracks) {
            if (!strack.isActivated) {
                unconfirmed.add(strack)
            } else {
                tracked.add(strack)
            }
        }
        
        strackPool.addAll(tracked)
        strackPool.addAll(lostStracks)
        
        // Kalman Predict
        for (strack in strackPool) {
            val pair = kalmanFilter.predict(strack.mean!!, strack.covariance!!)
            strack.mean = pair.first
            strack.covariance = pair.second
        }

        // 2. First Association (High Detections)
        // Match tracked + lost (pool) with High Detections
        val (dists, matches, uTrack, uDetection) = iouAssociation(strackPool, detectionsHigh, matchThresh)
        
        for ((idxTrack, idxDet) in matches) {
            val track = strackPool[idxTrack]
            val det = detectionsHigh[idxDet]
            if (track.state == TrackState.Tracked) {
                track.update(det, frameId)
                activatedStarcks.add(track)
            } else {
                track.reActivate(det, frameId, newId = false)
                refindStracks.add(track)
            }
        }

        // 3. Second Association (Low Detections)
        // Match unmatched tracks (only from 'tracked' list, not 'lost') with Low Detections
        
        val rTrackedStracks = ArrayList<STrack>()
        for (idx in uTrack) {
            val track = strackPool[idx]
            if (track.state == TrackState.Tracked) {
                rTrackedStracks.add(track)
            }
        }
        
        val (dists2, matches2, uTrack2, uDetection2) = iouAssociation(rTrackedStracks, detectionsLow, 0.5f)
        
        for ((idxTrack, idxDet) in matches2) {
            val track = rTrackedStracks[idxTrack]
            val det = detectionsLow[idxDet]
            if (track.state == TrackState.Tracked) {
                track.update(det, frameId)
                activatedStarcks.add(track)
            } else {
                 track.reActivate(det, frameId, newId = false)
                 refindStracks.add(track)
            }
        }

        // 3. New Tracks (Unmatched High Detections match with Unconfirmed)
        val uDetectionHighRemainder = ArrayList<STrack>()
        for (idx in uDetection) uDetectionHighRemainder.add(detectionsHigh[idx])
        
        val (dists3, matches3, uTrack3, uDetection3) = iouAssociation(unconfirmed, uDetectionHighRemainder, 0.7f)
         for ((idxTrack, idxDet) in matches3) {
            val track = unconfirmed[idxTrack]
            val det = uDetectionHighRemainder[idxDet]
            track.update(det, frameId)
            activatedStarcks.add(track)
        }
        
        // 4. Init New Tracks
        for (idx in uDetection3) {
            val det = uDetectionHighRemainder[idx]
            if (det.score >= trackThresh) { 
                det.activate(frameId, det.mask)
                activatedStarcks.add(det)
            }
        }
        
        // 5. Handle Lost
        // Rebuild lists
        trackedStracks.clear()
        trackedStracks.addAll(activatedStarcks)
        trackedStracks.addAll(refindStracks)
        
        val allActive = HashSet<Int>()
        activatedStarcks.forEach { allActive.add(it.trackId) }
        refindStracks.forEach { allActive.add(it.trackId) }
        
        // Rebuild lostStracks safely
        val nextLostStracks = ArrayList<STrack>()
        
        // Include previously lost tracks that are still lost
        for (track in lostStracks) {
            if (!allActive.contains(track.trackId)) {
                if (frameId - track.frameId <= trackBuffer) {
                    nextLostStracks.add(track)
                } else {
                    track.markRemoved()
                    removedStracks.add(track)
                }
            }
        }
        
        // Add newly lost tracks (from pool, if not active and not already in nextLost)
        // Actually, tracks from 'strackPool' (tracked + lost) that were not matched
        // Note: 'strackPool' contains 'lostStracks'.
        
        // Iterate only 'tracked' (original) to find newly lost
        for (track in tracked) { // 'tracked' valid at start of frame
            if (!allActive.contains(track.trackId) && track.state != TrackState.Lost) {
                 track.markLost()
                 nextLostStracks.add(track)
            }
        }
        
        // Also tracks from 'unconfirmed' (uTrack3) -> Removed (handled above?)
        // The original code handled unconfirmed unmatched as removed.
        for (idx in uTrack3) {
            val track = unconfirmed[idx]
            track.markRemoved()
            removedStracks.add(track)
        }
        
        lostStracks.clear()
        lostStracks.addAll(nextLostStracks)

        // Return combined
        val result = ArrayList<STrack>()
        result.addAll(trackedStracks)
        return result
    }

    // --- Association Utils ---
    private fun iouAssociation(tracks: List<STrack>, detections: List<STrack>, threshold: Float): AssociationResult {
        if (tracks.isEmpty() || detections.isEmpty()) {
            return AssociationResult(
                emptyArray(), emptyList(),
                tracks.indices.toList(), detections.indices.toList()
            )
        }
        
        val nTracks = tracks.size
        val nDets = detections.size
        
        // Optimization: Use flattened or primitive data structure to avoid allocating 2D array if possible
        // But for clarity/correctness first:
        // Candidates approach from previous fix:
        
        val candidates = ArrayList<MatchCandidate>()
        // capacity hint: nTracks * nDets? No, only overlapping. 
        // Iterate all pairs? Costly.
        // Assuming typical N is small (<100).
        
        for (i in 0 until nTracks) {
            val track = tracks[i]
            for (j in 0 until nDets) {
                val det = detections[j]
                
                // Box IoU
                // We need to use tlbr (top left bottom right)
                // STrack internal: tlwh?
                // track.tlbr
                val iou = iou(track.tlbr, det.tlbr)
                val cost = 1.0f - iou
                
                if (cost <= threshold) {
                    candidates.add(MatchCandidate(cost, i, j))
                }
            }
        }
        
        candidates.sortBy { it.cost }
        
        val matchedTracks = BooleanArray(nTracks)
        val matchedDets = BooleanArray(nDets)
        val matches = ArrayList<Pair<Int, Int>>()
        
        // Greedy assignment
        for (cand in candidates) {
            if (!matchedTracks[cand.trackIdx] && !matchedDets[cand.detIdx]) {
                matchedTracks[cand.trackIdx] = true
                matchedDets[cand.detIdx] = true
                matches.add(Pair(cand.trackIdx, cand.detIdx))
            }
        }
        
        val uTrack = ArrayList<Int>()
        for (i in 0 until nTracks) {
            if (!matchedTracks[i]) uTrack.add(i)
        }
        
        val uDetection = ArrayList<Int>()
        for (j in 0 until nDets) {
            if (!matchedDets[j]) uDetection.add(j)
        }
        
        return AssociationResult(emptyArray(), matches, uTrack, uDetection)
    }
    
    private fun iou(box1: FloatArray, box2: FloatArray): Float {
        val b1_x1 = box1[0]
        val b1_y1 = box1[1]
        val b1_x2 = box1[2]
        val b1_y2 = box1[3]
        
        val b2_x1 = box2[0]
        val b2_y1 = box2[1]
        val b2_x2 = box2[2]
        val b2_y2 = box2[3]

        val xx1 = max(b1_x1, b2_x1)
        val yy1 = max(b1_y1, b2_y1)
        val xx2 = min(b1_x2, b2_x2)
        val yy2 = min(b1_y2, b2_y2)

        val w = max(0.0f, xx2 - xx1)
        val h = max(0.0f, yy2 - yy1)
        val inter = w * h

        val area1 = (b1_x2 - b1_x1) * (b1_y2 - b1_y1)
        val area2 = (b2_x2 - b2_x1) * (b2_y2 - b2_y1)
        val union = area1 + area2 - inter + 1e-6f

        return inter / union
    }

    private data class AssociationResult(
        val costMatrix: Array<FloatArray>, // Unused now
        val matches: List<Pair<Int, Int>>,
        val unmatchedTracks: List<Int>,
        val unmatchedDetections: List<Int>
    )
    
    private data class MatchCandidate(val cost: Float, val trackIdx: Int, val detIdx: Int)
}
