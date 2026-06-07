package com.videokiosk.kiosk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.videokiosk.kiosk.R
import com.videokiosk.kiosk.viewmodel.MainViewModel
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * Active call screen showing local and remote video streams.
 * Manages [SurfaceViewRenderer] lifecycle and attaches them to [WebRTCClient].
 */
class CallFragment : Fragment() {

    companion object {
        private const val TAG = "CallFragment"
    }

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var btnEndCall: Button

    // ---------------------------------------------------------------------------
    // Permission launcher
    // ---------------------------------------------------------------------------

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            initRenderers()
        } else {
            // TODO: show explanation and navigate back
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
        btnEndCall = view.findViewById(R.id.btn_end_call)

        btnEndCall.setOnClickListener {
            viewModel.endCall()
        }

        // Check permissions before initializing renderers
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            initRenderers()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release renderer resources to avoid memory leaks
        remoteRenderer.release()
        localRenderer.release()
    }

    // ---------------------------------------------------------------------------
    // Renderer initialization
    // ---------------------------------------------------------------------------

    private fun initRenderers() {
        // Use the EGL context from the WebRTC engine so renderers share the same GL context
        // as the video capturer, enabling zero-copy texture rendering.
        val eglContext = viewModel.getWebRTCEglContext()
        if (eglContext == null) {
            Log.w(TAG, "WebRTC EGL context not yet available — renderer init deferred")
            // Retry in the next frame; the ViewModel initializes WebRTC asynchronously
            remoteRenderer.postDelayed({ initRenderers() }, 100)
            return
        }

        Log.i(TAG, "Initialising SurfaceViewRenderers with WebRTC EGL context")

        remoteRenderer.init(eglContext, null)
        remoteRenderer.setEnableHardwareScaler(true)
        remoteRenderer.setMirror(false)

        localRenderer.init(eglContext, null)
        localRenderer.setEnableHardwareScaler(true)
        localRenderer.setMirror(true) // mirror front-facing camera preview

        // Wire renderers to WebRTCClient via ViewModel
        viewModel.attachCallRenderers(localRenderer, remoteRenderer)
        Log.i(TAG, "SurfaceViewRenderers attached to WebRTC")
    }
}
