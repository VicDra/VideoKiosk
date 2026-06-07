package com.videokiosk.kiosk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var btnEndCall: Button

    // EGL context shared between local and remote renderers
    private val rootEglBase: EglBase by lazy { EglBase.create() }

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
        rootEglBase.release()
    }

    // ---------------------------------------------------------------------------
    // Renderer initialization
    // ---------------------------------------------------------------------------

    private fun initRenderers() {
        // Initialize SurfaceViewRenderers with the shared EGL context
        remoteRenderer.init(rootEglBase.eglBaseContext, null)
        remoteRenderer.setEnableHardwareScaler(true)
        remoteRenderer.setMirror(false)

        localRenderer.init(rootEglBase.eglBaseContext, null)
        localRenderer.setEnableHardwareScaler(true)
        localRenderer.setMirror(true) // mirror local camera preview

        // TODO: obtain WebRTCClient from ViewModel and call:
        //   webRTCClient.attachLocalRenderer(localRenderer)
        //   webRTCClient.attachRemoteRenderer(remoteRenderer)
        //   webRTCClient.startLocalVideo(requireContext())
    }
}
