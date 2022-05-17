package com.example.cameraxilluminative

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.cameraxilluminative.databinding.FragmentFullScreenPhotoBinding

class FullScreenPhotoFragment : Fragment() {
    private lateinit var binding: FragmentFullScreenPhotoBinding

    var uri: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFullScreenPhotoBinding.inflate(layoutInflater)

        binding.image.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStackImmediate()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        uri?.let {
            Glide.with(binding.image)
                .load(it)
                .into(binding.image)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
