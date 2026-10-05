package com.appguard.blocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
    private val allRows = mutableListOf<SigRow>()
    private val visible = mutableListOf<SigRow>()
    private lateinit var adapter: SignatureAdapter
    private var status = SignatureStatus.PENDING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignaturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)
        prefs.backfillWhitelistFromAllowedPackages()

        adapter = SignatureAdapter(
            visible,
            onApprove = { approve(it) },
            onDelete = { delete(it) }
        )
        binding.signatureList.layoutManager = LinearLayoutManager(this)
        binding.signatureList.adapter = adapter

        binding.searchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        binding.btnSelectAll.setOnClickListener {
            allRows.forEach { it.picked = true }
            adapter.notifyDataSetChanged()
        }
        binding.btnDeselectAll.setOnClickListener {
            allRows.forEach { it.picked = false }
            adapter.notifyDataSetChanged()
        }
        binding.btnSave.setOnClickListener { save() }

        binding.statusToggle.check(R.id.tabPending)
        binding.statusToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            status = if (checkedId == R.id.tabWhite) SignatureStatus.WHITE else SignatureStatus.PENDING
            reload()
        }
        reload()
    }

    private fun approve(row: SigRow) {
        applyApprove(row.record)
        allRows.remove(row)
        applyFilter()
    }

    /** Drops the decision. Opening the installed app puts it back in pending. */
    private fun delete(row: SigRow) {
        prefs.deleteSignature(row.record.sha256)
        allRows.remove(row)
        applyFilter()
    }

    private fun save() {
        val snapshot = allRows.toList()
        for (row in snapshot) {
            if (status == SignatureStatus.PENDING && row.picked) applyApprove(row.record)
            if (status == SignatureStatus.WHITE && !row.picked) prefs.deleteSignature(row.record.sha256)
        }
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        reload()
    }

    private fun applyApprove(record: SignatureRecord) {
        prefs.putSignature(record.sha256, record.packageName, record.label, SignatureStatus.WHITE)
        val installed = SigningCerts.installedPackagesWithSignature(this, record.sha256)
        if (installed.isEmpty()) prefs.allowPackage(record.packageName)
        else installed.forEach { prefs.allowPackage(it) }
    }

    private fun reload() {
        allRows.clear()
        allRows.addAll(
            prefs.signatures(status).map { SigRow(it, picked = status == SignatureStatus.WHITE) }
        )
        adapter.showApprove = status == SignatureStatus.PENDING
        applyFilter()
    }

    private fun applyFilter() {
        val query = binding.searchApps.text?.toString().orEmpty().trim().lowercase()
        visible.clear()
        if (query.isBlank()) visible.addAll(allRows)
        else visible.addAll(
            allRows.filter {
                it.record.label.lowercase().contains(query) ||
                    it.record.packageName.lowercase().contains(query)
            }
        )
        adapter.notifyDataSetChanged()
        val empty = visible.isEmpty()
        binding.emptySignatures.visibility = if (empty) View.VISIBLE else View.GONE
        binding.signatureList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptySignatures.setText(
            when {
                query.isNotBlank() -> R.string.sig_search_empty
                status == SignatureStatus.WHITE -> R.string.sig_empty_white
                else -> R.string.sig_empty_pending
            }
        )
        binding.sigHint.setText(
            if (status == SignatureStatus.WHITE) R.string.sig_white_hint else R.string.sig_pending_hint
        )
    }

    private data class SigRow(val record: SignatureRecord, var picked: Boolean)

    private class SignatureAdapter(
        private val items: List<SigRow>,
        private val onApprove: (SigRow) -> Unit,
        private val onDelete: (SigRow) -> Unit
    ) : RecyclerView.Adapter<SignatureAdapter.VH>() {

        var showApprove: Boolean = true

        class VH(val binding: ItemSignatureBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSignatureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val ctx = holder.binding.root.context
            holder.binding.sigLabel.text = row.record.label.ifBlank { row.record.packageName }
            holder.binding.sigPackage.text = row.record.packageName
            val icon = runCatching {
                ctx.packageManager.getApplicationIcon(row.record.packageName)
            }.getOrNull()
            if (icon != null) holder.binding.sigIcon.setImageDrawable(icon)
            else holder.binding.sigIcon.setImageResource(R.drawable.ic_launcher_foreground)
            holder.binding.btnSigApprove.visibility = if (showApprove) View.VISIBLE else View.GONE
            holder.binding.btnSigApprove.setOnClickListener { onApprove(row) }
            holder.binding.btnSigDelete.setOnClickListener { onDelete(row) }
            holder.binding.sigPicked.setOnCheckedChangeListener(null)
            holder.binding.sigPicked.isChecked = row.picked
            holder.binding.sigPicked.setOnCheckedChangeListener { _, checked -> row.picked = checked }
            holder.binding.root.setOnClickListener {
                holder.binding.sigPicked.isChecked = !holder.binding.sigPicked.isChecked
            }
        }
    }
}
