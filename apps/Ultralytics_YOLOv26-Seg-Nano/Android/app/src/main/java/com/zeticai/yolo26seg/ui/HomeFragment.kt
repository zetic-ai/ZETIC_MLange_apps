package com.zeticai.yolo26seg.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.zeticai.yolo26seg.R
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialCardView>(R.id.cardCamera).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_camera)
        }

        view.findViewById<MaterialCardView>(R.id.cardUpload).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_upload)
        }
    }
}
