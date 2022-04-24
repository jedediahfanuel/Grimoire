package com.ppb.grimoire

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textview.MaterialTextView
import com.ppb.grimoire.MainActivity.Companion.NtHelp
import com.ppb.grimoire.db.DatabaseContract
import com.ppb.grimoire.db.DatabaseContract.NoteColumns.Companion.DATE
import com.ppb.grimoire.db.NoteHelper
import com.ppb.grimoire.model.Note
import com.ppb.grimoire.model.NoteElement
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class NoteAddUpdateActivity : AppCompatActivity(), View.OnClickListener {
    private var isEdit = false
    private var note: Note? = null
    private var position: Int = 0

    private lateinit var uri: Uri
    private lateinit var addImage: LinearLayout
    private lateinit var addText: LinearLayout
    private lateinit var submit: LinearLayout
    private lateinit var show: LinearLayout

    private lateinit var noteHelper: NoteHelper
    private lateinit var edtTitle: EditText
    private lateinit var edtDescription: EditText
    private lateinit var btnSubmit: ImageView

    private var noteElement = ArrayList<NoteElement>()
    private lateinit var elementLayout: LinearLayout

    private var account: GoogleSignInAccount? = null
    private lateinit var personId: String

    companion object {
        const val SELECT_IMAGE = 1
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_POSITION = "extra_position"
        const val REQUEST_ADD = 100
        const val RESULT_ADD = 101
        const val REQUEST_UPDATE = 200
        const val RESULT_UPDATE = 201
        const val RESULT_DELETE = 301
        const val ALERT_DIALOG_CLOSE = 10
        const val ALERT_DIALOG_DELETE = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_add_update)

        edtTitle = findViewById(R.id.edt_title)
        edtDescription = findViewById(R.id.edt_description)
        btnSubmit = findViewById(R.id.btn_submit)

        noteHelper = NtHelp

        note = intent.getParcelableExtra(EXTRA_NOTE)
        if (note != null) {
            position = intent.getIntExtra(EXTRA_POSITION, 0)
            isEdit = true
        } else {
            note = Note()
        }

        val actionBarTitle: String

        if (isEdit) {
            actionBarTitle = "Edit"

            note?.let {
                edtTitle.setText(it.title)
                edtDescription.setText(it.description)
            }
        } else {
            actionBarTitle = "Insert"
        }

        supportActionBar?.title = actionBarTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btnSubmit.setOnClickListener(this)

        initMiscellaneous()
        initElement()
        initAccount()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_submit -> {
                processBasicData()
                processElementData()
            }
            R.id.layoutAddImage -> {
                pickImage()
            }
            R.id.layoutAddText -> {
                addTextView()
            }
            R.id.layoutSubmit -> {
                saveData()
            }
            R.id.layoutShowData -> {
                showData()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_IMAGE && resultCode == RESULT_OK && data?.data != null) {
            uri = data.data!!
            try {
                // Update Image View w/ gambar yang telah dipilih dari storage hp
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                addImageView(bitmap)
                noteElement.add(NoteElement(0, uri.toString(), "image", personId,0, 0))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isEdit) {
            menuInflater.inflate(R.menu.menu_form, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> showAlertDialog(ALERT_DIALOG_DELETE)
            android.R.id.home -> showAlertDialog(ALERT_DIALOG_CLOSE)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        showAlertDialog(ALERT_DIALOG_CLOSE)
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val date = Date()

        return dateFormat.format(date)
    }

    private fun showAlertDialog(type: Int) {
        val isDialogClose = type == ALERT_DIALOG_CLOSE
        val dialogTitle: String
        val dialogMessage: String

        if (isDialogClose) {
            dialogTitle = "Cancel"
            dialogMessage = "Do you wish to cancel edit the note?"
        } else {
            dialogMessage = "Do you wish to delete this beautiful note?"
            dialogTitle = "Delete Note"
        }

        val alertDialogBuilder = AlertDialog.Builder(this)

        alertDialogBuilder.setTitle(dialogTitle)
        alertDialogBuilder.setMessage(dialogMessage).setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                if (isDialogClose) {
                    finish()
                } else {
                    val result = noteHelper.deleteById(note?.id.toString()).toLong()
                    if (result > 0) {
                        val intent = Intent()
                        intent.putExtra(EXTRA_POSITION, position)
                        setResult(RESULT_DELETE, intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@NoteAddUpdateActivity,
                            "Delete data failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("No") { dialog, id -> dialog.cancel() }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, SELECT_IMAGE)
    }

    @SuppressLint("InflateParams")
    private fun addImageView(bitmap: Bitmap) {
        val inflater = LayoutInflater.from(this).inflate(R.layout.element_note_image, null)
        elementLayout.addView(inflater)
        elementLayout.getChildAt(elementLayout.childCount - 1)
            .findViewById<ImageView>(R.id.elm_image)
            .setImageBitmap(bitmap)
    }

    @SuppressLint("InflateParams")
    private fun addTextView() {
        val inflater = LayoutInflater.from(this).inflate(R.layout.element_note_text, null)
        elementLayout.addView(inflater)
        noteElement.add(NoteElement(0, "", "text", personId,0, 0))
    }

    private fun processBasicData() {
        val title = edtTitle.text.toString().trim()
        val description = edtDescription.text.toString().trim()

        if (title.isEmpty()) {
            edtTitle.error = "Field can not be blank"
            return
        }

        note?.title = title
        note?.personId = personId
        note?.description = description

        val intent = Intent()
        intent.putExtra(EXTRA_NOTE, note)
        intent.putExtra(EXTRA_POSITION, position)

        val values = ContentValues()
        values.put(DatabaseContract.NoteColumns.PERSON_ID, personId)
        values.put(DatabaseContract.NoteColumns.TITLE, title)
        values.put(DatabaseContract.NoteColumns.DESCRIPTION, description)

        if (isEdit) {
            val result = noteHelper.update(note?.id.toString(), values).toLong()
            if (result > 0) {
                setResult(RESULT_UPDATE, intent)
                finish()
            } else {
                Toast.makeText(
                    this@NoteAddUpdateActivity,
                    "Update data failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            note?.date = getCurrentDate()
            values.put(DATE, getCurrentDate())
            val result = noteHelper.insert(values)

            if (result > 0) {
                note?.id = result.toInt()
                setResult(RESULT_ADD, intent)
                finish()
            } else {
                Toast.makeText(
                    this@NoteAddUpdateActivity,
                    "Insert data failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun processElementData() {
        val noteId = note?.id.toString()
    }

    private fun saveData() {
        // Hitung jumlah child di
        // Element Linear Layout
        val count = elementLayout.childCount
        var v: View?

        // Untuk setiap element
        for (i in 0 until count) {
            v = elementLayout.getChildAt(i)

            if (noteElement[i].type == "text") {
                val elm: EditText =  v.findViewById(R.id.elm_text)
                noteElement[i].str = elm.text.toString()
            }/* else if (noteElement[i].type == "image") {
                val elm: ImageView =  v.findViewById(R.id.elm_image)
            }*/
        }
    }

    private fun showData() {
        val count = elementLayout.childCount
        for (i in 0 until count) {
            Toast.makeText(this,
                "Element at $i is ${noteElement[i].str}.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initElement() {
        elementLayout = findViewById(R.id.parent_linear_layout)

        addImage = findViewById(R.id.layoutAddImage)
        addText = findViewById(R.id.layoutAddText)
        submit = findViewById(R.id.layoutSubmit)
        show = findViewById(R.id.layoutShowData)

        addImage.setOnClickListener(this)
        addText.setOnClickListener(this)
        submit.setOnClickListener(this)
        show.setOnClickListener(this)
    }

    private fun initMiscellaneous() {
        val layoutMiscellaneous = findViewById<LinearLayout>(R.id.layoutMiscellaneous)
        val bottomSheetBehavior = BottomSheetBehavior.from(layoutMiscellaneous)
        layoutMiscellaneous.findViewById<MaterialTextView>(R.id.textMiscellaneous).setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
    }

    private fun initAccount() {
        account = GoogleSignIn.getLastSignedInAccount(this)
        personId = account?.id.toString()
    }

}