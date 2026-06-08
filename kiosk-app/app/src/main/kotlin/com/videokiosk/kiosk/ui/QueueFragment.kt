package com.videokiosk.kiosk.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.videokiosk.kiosk.R
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Connecting / queue screen (Screen 10).
 * Navy gradient stage with two pulsing rings (ObjectAnimator), a wait-badge
 * timer, a PiP self-camera placeholder (bottom-right), and a cancel button.
 *
 * View IDs:
 *   view_ring1         — outer pulse ring (animated from scale 0.38→1.7)
 *   view_ring2         — inner pulse ring (same, 700ms delayed)
 *   tv_estimated_time  — wait-badge label (updated from CallState)
 *   tv_queue_position  — GONE; kept for build compatibility
 *   btn_cancel_call    — red pill button → endCall()
 *   btn_topbar_cancel  — topbar "‹ Отмена" pill → endCall()
 */
class QueueFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvEstimatedTime: TextView
    private lateinit var btnCancelCall: View

    // Pulse animators — cancelled in onDestroyView
    private var pulseAnimators: List<AnimatorSet> = emptyList()

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_queue, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvEstimatedTime = view.findViewById(R.id.tv_estimated_time)
        btnCancelCall = view.findViewById(R.id.btn_cancel_call)

        // Both cancel controls end the call
        btnCancelCall.setOnClickListener { viewModel.endCall() }
        view.findViewById<View>(R.id.btn_topbar_cancel).setOnClickListener { viewModel.endCall() }

        // Start pulse animation on both rings
        val ring1 = view.findViewById<View>(R.id.view_ring1)
        val ring2 = view.findViewById<View>(R.id.view_ring2)
        pulseAnimators = listOf(
            buildPulseAnimator(ring1, startDelay = 0L),
            buildPulseAnimator(ring2, startDelay = 700L)
        )
        pulseAnimators.forEach { it.start() }

        // Observe call state — update badge label
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Queued -> {
                        val mins = state.position * 3
                        tvEstimatedTime.text = getString(
                            R.string.queue_wait_badge,
                            getString(R.string.queue_estimated_minutes, mins)
                        )
                    }
                    is CallState.Calling -> {
                        tvEstimatedTime.text = getString(R.string.queue_wait_connecting)
                    }
                    else -> { /* navigation handled by MainActivity */ }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimators.forEach { it.cancel() }
    }

    // ---------------------------------------------------------------------------
    // Pulse animation builder
    // ---------------------------------------------------------------------------

    /**
     * Builds an AnimatorSet that scales [view] from 0.38→1.7 and fades alpha
     * from 0.7→0 over 2200ms, repeating infinitely.
     * Mirrors the CSS: `@keyframes vc-pulse { 0% scale(1) 0.7 → 100% scale(1.7) 0 }`
     * (we start at 0.38 so the ring appears to grow from the core centre outward).
     */
    private fun buildPulseAnimator(view: View, startDelay: Long): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.38f, 1.7f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.38f, 1.7f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.7f, 0f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            this.startDelay = startDelay
        }
    }
}
