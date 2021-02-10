package com.example.verifonevx990app.emv.transactionprocess

import android.app.Activity
import android.app.AlertDialog
import android.os.*
import android.util.Log
import com.example.verifonevx990app.realmtables.TerminalParameterTable
import com.example.verifonevx990app.vxUtils.ProcessingCode
import com.example.verifonevx990app.vxUtils.VFService
import com.example.verifonevx990app.vxUtils.VerifoneApp
import com.example.verifonevx990app.vxUtils.forceStart
import com.vfi.smartpos.deviceservice.aidl.IEMV

import com.vfi.smartpos.deviceservice.constdefine.ConstIPBOC
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DoEmv(
    var activity: Activity,
    var handler: Handler,
    var cardProcessedDataModal: CardProcessedDataModal,
    valueCardTypeSmartCard: Int,
    var transactionCallback: (CardProcessedDataModal) -> Unit
) {
    private val iemv: IEMV? by lazy { VFService.vfIEMV }

    //    private var iemv: IEMV? = VFService.vfIEMV
    var transactionalAmount = cardProcessedDataModal.getTransactionAmount() ?: 0

    init {
        startEMVProcess(valueCardTypeSmartCard, transactionalAmount)
    }

    // region ========================== First GEN AC
    private fun startEMVProcess(type: Int, transactionalAmount: Long) {
        try {
            val terminalParameterTable = TerminalParameterTable.selectFromSchemeTable()
            val emvIntent = Bundle()
            emvIntent.putInt(ConstIPBOC.startEMV.intent.KEY_cardType_int, type)
            emvIntent.putLong(ConstIPBOC.startEMV.intent.KEY_authAmount_long, transactionalAmount)
            emvIntent.putString(
                ConstIPBOC.startEMV.intent.KEY_merchantName_String,
                terminalParameterTable?.receiptHeaderTwo
            )
            emvIntent.putString(
                ConstIPBOC.startEMV.intent.KEY_merchantId_String,
                terminalParameterTable?.merchantId
            )
            emvIntent.putString(
                ConstIPBOC.startEMV.intent.KEY_terminalId_String,
                terminalParameterTable?.terminalId
            )
            emvIntent.putBoolean(
                ConstIPBOC.startEMV.intent.KEY_isSupportQ_boolean,
                ConstIPBOC.startEMV.intent.VALUE_supported
            )
            emvIntent.putBoolean(
                ConstIPBOC.startEMV.intent.KEY_isSupportSM_boolean,
                ConstIPBOC.startEMV.intent.VALUE_unsupported
            )
            emvIntent.putBoolean(
                ConstIPBOC.startEMV.intent.KEY_isQPBOCForceOnline_boolean,
                ConstIPBOC.startEMV.intent.VALUE_unforced
            )
            emvIntent.putBoolean("isForceOffline", false)
            if (type == ConstIPBOC.startEMV.intent.VALUE_cardType_contactless) {
                emvIntent.putByte(
                    ConstIPBOC.startEMV.intent.KEY_transProcessCode_byte,
                    0x00.toByte()
                )
            }
            //Below we are setting 9C for all Type of Transaction CTLS & EMV:-
            when (cardProcessedDataModal.getProcessingCode()) {
                ProcessingCode.REFUND.code -> emvIntent.putByte(
                    ConstIPBOC.startEMV.intent.KEY_transProcessCode_byte,
                    0x20.toByte()
                ) //------> For Refund Transaction

                ProcessingCode.SALE_WITH_CASH.code -> emvIntent.putByte(
                    ConstIPBOC.startEMV.intent.KEY_transProcessCode_byte,
                    0x09.toByte()
                ) //------> For Sale with Cash Transaction

                ProcessingCode.CASH_AT_POS.code -> emvIntent.putByte(
                    ConstIPBOC.startEMV.intent.KEY_transProcessCode_byte,
                    0x01.toByte()
                ) //------> For Cash Transaction

                else -> emvIntent.putByte(
                    ConstIPBOC.startEMV.intent.KEY_transProcessCode_byte,
                    0x00.toByte()
                ) //------> For Sale Transaction
            }

            emvIntent.putBoolean("isSupportPBOCFirst", false)
            val TRASNSCURRENTCODE = "transCurrCode"
            val OTHERAMOUNT = "otherAmount"
            emvIntent.putString(TRASNSCURRENTCODE, "0356")
            emvIntent.putString(OTHERAMOUNT, "0")
            iemv?.startEMV(ConstIPBOC.startEMV.processType.full_process, emvIntent, emvHandler())
        } catch (ex: DeadObjectException) {
            ex.printStackTrace()
            println("DoEmv card error1" + ex.message)
            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                GlobalScope.launch {
                    VFService.connectToVFService(VerifoneApp.appContext)
                    delay(200)
                    //  iemv = VFService.vfIEMV
                    delay(100)
                    (activity as VFTransactionActivity).doProcessCard()
                }
            }, 200)
        } catch (e: RemoteException) {
            e.printStackTrace()
            println("DoEmv card error2" + e.message)
            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                GlobalScope.launch {
                    VFService.connectToVFService(VerifoneApp.appContext)
                    delay(200)
                    //        iemv = VFService.vfIEMV
                    delay(100)
                    (activity as VFTransactionActivity).doProcessCard()
                }
            }, 200)
        } catch (e: Exception) {
            e.printStackTrace()
            println("DoEmv card error3" + e.message)
            val builder = AlertDialog.Builder(activity)
            object : Thread() {
                override fun run() {
                    Looper.prepare()
                    builder.setTitle("Alert...!!")
                    builder.setCancelable(false)
                    builder.setMessage(
                        "Service stopped , " +
                                "Please reinitiate the transaction."
                    )
                        .setCancelable(false)
                        .setPositiveButton("Start") { _, _ ->
                            forceStart(activity)
                        }
                        .setNeutralButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                            activity.finishAffinity()
                        }
                    val alert: AlertDialog? = builder.create()
                    alert?.show()
                    Looper.loop()
                }
            }.start()
        }
    }
    //endregion

    //region========================================Below Method is a Handler for EMV CardType:-
    private fun emvHandler(): VFEmvHandler {
        println("DoEmv VfemvHandler is calling")
        println("iemv value is" + iemv.toString())
        return VFEmvHandler(activity, handler, iemv, cardProcessedDataModal) { cardProcessedData ->
            transactionCallback(cardProcessedData)
            Log.d("Track2Data:- ", cardProcessedData.getTrack2Data() ?: "")
            Log.d("PanNumber:- ", cardProcessedData.getPanNumberData() ?: "")
        }
    }
    //endregion
}