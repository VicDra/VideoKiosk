package com.videokiosk.kiosk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.videokiosk.kiosk.R
import com.videokiosk.kiosk.viewmodel.MainViewModel
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import java.util.Locale

/**
 * Active call screen (Screen 11).
 * Full-screen remote video + PiP local preview (top-right, 210×146dp).
 * Live-badge + elapsed timer overlay at top.
 * Controls: mic toggle · camera toggle · end call (96dp red circle) at bottom.
 *
 * View IDs:
 *   remote_renderer    — full-screen SurfaceViewRenderer for operator video
 *   local_renderer     — PiP SurfaceViewRenderer for local camera
 *   local_pip_container— FrameLayout wrapper (clip-to-outline rounded)
 *   tv_call_live_label — "Идёт разговор" badge label
 *   tv_call_timer      — "MM:SS" elapsed time counter
 *   btn_mute           — microphone toggle (LinearLayout)
 *   btn_camera         — camera toggle (LinearLayout)
 *   btn_end_call       — end call (LinearLayout, 96dp red circle)
 */
class CallFragment : Fragment() {

    companion object {
        private const val TAG = "CallFragment"
    }

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var btnEndCall: View
    private lateinit var tvCallTimer: TextView

    // Call-timer state
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callSeconds = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            callSeconds++
            tvCallTimer.text = formatSeconds(callSeconds)
            timerHandler.postDelayed(this, 1_000L)
        }
    }

    // ---------------------------------------------------------------------------
    // Permission launcher
    // ---------------------------------------------------------------------------

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initRenderers()
        } else {
            viewModel.endCall()
        }
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_call, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        remoteRenderer = view.findViewById(R.id.remote_renderer)
        localRenderer = view.findViewById(R.id.local_renderer)
        tvCallTimer = view.findViewById(R.id.tv_call_timer)
        btnEndCall = view.findViewById(R.id.btn_end_call)

        // Clip local PiP to rounded corners (outline from shape_pip_frame background)
        view.findViewById<View>(R.id.local_pip_container).let { pip ->
            pip.clipToOutline = true
        }

        // Controls
        btnEndCall.setOnClickListener { viewModel.endCall() }
        view.findViewById<View>(R.id.btn_mute).setOnClickListener {
            viewModel.toggleMute()
        }
        view.findViewById<View>(R.id.btn_camera).setOnClickListener {
            viewModel.toggleCamera()
        }

        // Start elapsed timer
        callSeconds = 0
        timerHandler.postDelayed(timerRunnable, 1_000L)

        // Camera + mic permissions
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
            }) {
            initRenderers()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        remoteRenderer.release()
        localRenderer.release()
    }

    // ---------------------------------------------------------------------------
    // Renderer initialization
    // ---------------------------------------------------------------------------

    private fun initRenderers() {
        val eglContext = viewModel.getWebRTCEglContext()
        if (eglContext == null) {
            Log.w(TAG, "WebRTC EGL context not yet available — retrying in 100ms")
            remoteRenderer.postDelayed({ initRenderers() }, 100)
            return
        }

        Log.i(TAG, "Initialising SurfaceViewRenderers")

        remoteRenderer.init(eglContext, null)
        remoteRenderer.setEnableHardwareScaler(true)
        remoteRenderer.setMirror(false)

        localRenderer.init(eglContext, null)
        localRenderer.setEnableHardwareScaler(true)
        localRenderer.setMirror(true)

        viewModel.attachCallRenderers(localRenderer, remoteRenderer)
        Log.i(TAG, "SurfaceViewRenderers attached to WebRTC")
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun formatSeconds(total: Int): String {
        val m = total / 60
        val s = total % 60
        return String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}
