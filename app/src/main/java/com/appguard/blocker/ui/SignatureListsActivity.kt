package com.appguard.blocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.data.SignatureRecord
import com.appguard.blocker.data.SignatureStatus
import com.appguard.blocker.data.SigningCerts
import com.appguard.blocker.databinding.ActivitySignaturesBinding
import com.appguard.blocker.databinding.ItemSignatureBinding

class SignatureListsActivity : SecureActivity() {

    private lateinit var binding: ActivitySignaturesBinding
    private lateinit var prefs: PrefsRepository
    private val rows = mutableListOf<SignatureRecord>()
    private lateinit var adapter: SignatureAdapter
    private var status = SignatureStatus.PENDING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignaturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)
        prefs.backfillWhitelistFromAllowedPackages()

        adapter = SignatureAdapter(
            rows,
            onApprove = { approve(it) },
            onDelete = { delete(it) }
        )
        binding.signatureList.layoutManager = LinearLayoutManager(this)
        binding.signatureList.adapter = adapter

        binding.statusToggle.check(R.id.tabPending)
        binding.statusToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            status = if (checkedId == R.id.tabWhite) SignatureStatus.WHITE else SignatureStatus.PENDING
            reload()
        }
        reload()
    }

    private fun approve(record: SignatureRecord) {
        prefs.putSignature(record.sha256, record.packageName, record.label, SignatureStatus.WHITE)
        val installed = SigningCerts.installedPackagesWithSignature(this, record.sha256)
        if (installed.isEmpty()) prefs.allowPackage(record.packageName)
        else installed.forEach { prefs.allowPackage(it) }
        reload()
    }

    /** Drops the decision. Opening the installed app puts it back in pending. */
    private fun delete(record: SignatureRecord) {
        prefs.deleteSignature(record.sha256)
        reload()
    }

    private fun reload() {
        rows.clear()
        rows.addAll(prefs.signatures(status))
        adapter.showApprove = status == SignatureStatus.PENDING
        adapter.notifyDataSetChanged()
        val empty = rows.isEmpty()
        binding.emptySignatures.visibility = if (empty) View.VISIBLE else View.GONE
        binding.signatureList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptySignatures.setText(
            if (status == SignatureStatus.WHITE) R.string.sig_empty_white else R.string.sig_empty_pending
        )
        binding.sigHint.setText(
            if (status == SignatureStatus.WHITE) R.string.sig_white_hint else R.string.sig_pending_hint
        )
    }

    private class SignatureAdapter(
        private val items: List<SignatureRecord>,
        private val onApprove: (SignatureRecord) -> Unit,
        private val onDelete: (SignatureRecord) -> Unit
    ) : RecyclerView.Adapter<SignatureAdapter.VH>() {

        var showApprove: Boolean = true

        class VH(val binding: ItemSignatureBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSignatureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.binding.root.context
            holder.binding.sigLabel.text = item.label.ifBlank { item.packageName }
            holder.binding.sigPackage.text = item.packageName
            val icon = runCatching { ctx.packageManager.getApplicationIcon(item.packageName) }.getOrNull()
            if (icon != null) holder.binding.sigIcon.setImageDrawable(icon)
            else holder.binding.sigIcon.setImageResource(R.drawable.ic_launcher_foreground)
            holder.binding.btnSigApprove.visibility = if (showApprove) View.VISIBLE else View.GONE
            holder.binding.btnSigApprove.setOnClickListener { onApprove(item) }
            holder.binding.btnSigDelete.setOnClickListener { onDelete(item) }
        }
    }
}
