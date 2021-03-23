package com.example.verifonevx990app.brandemibyaccesscode

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.verifonevx990app.R
import com.example.verifonevx990app.brandemi.CreateBrandEMIPacket
import com.example.verifonevx990app.databinding.BrandEmiByAccessCodeViewBinding
import com.example.verifonevx990app.main.EMIRequestType
import com.example.verifonevx990app.main.MainActivity
import com.example.verifonevx990app.main.SplitterTypes
import com.example.verifonevx990app.vxUtils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize

class BrandEMIByAccessCodeFragment : Fragment() {
    private val action by lazy { arguments?.getSerializable("type") ?: "" }
    private var iDialog: IDialog? = null
    private var binding: BrandEmiByAccessCodeViewBinding? = null
    private var field57Request: String? = null
    private var totalRecord: String = "0"
    private val brandEmiAccessCodeList: MutableList<BrandEMIAccessDataModal> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = BrandEmiByAccessCodeViewBinding.inflate(layoutInflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).showBottomNavigationBar(isShow = false)
        binding?.subHeaderView?.subHeaderText?.text = getString(R.string.brand_emi_by_access_code)
        binding?.subHeaderView?.backImageButton?.setOnClickListener { parentFragmentManager.popBackStackImmediate() }

        binding?.brandEmiAccessCodeBT?.setOnClickListener {
            if (!TextUtils.isEmpty(binding?.accessCodeET?.text?.toString()))
                fetchBrandEmiDataByAccessCode(binding?.accessCodeET?.text?.toString())
            else
                VFService.showToast(getString(R.string.please_enter_access_code))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is IDialog) iDialog = context
    }

    override fun onDetach() {
        super.onDetach()
        iDialog = null
    }

    //region===========================Fetching Brand EMI Data by Access Code:-
    private fun fetchBrandEmiDataByAccessCode(accessCode: String?) {
        field57Request =
            "${EMIRequestType.BRAND_EMI_BY_ACCESS_CODE.requestType}^0^$totalRecord^$accessCode"
        Log.d("Request:- ", field57Request.toString())
        iDialog?.showProgress()
        var brandEMIProcessData: IsoDataWriter? = null
        //region==============================Creating ISO Packet For BrandEMIMasterSubCategoryData Request:-
        runBlocking(Dispatchers.IO) {
            CreateBrandEMIPacket(field57Request) {
                brandEMIProcessData = it
            }
        }

        //region==============================Host Hit To Fetch BrandEMIAccessCode Data:-
        GlobalScope.launch(Dispatchers.IO) {
            if (brandEMIProcessData != null) {
                val byteArrayRequest = brandEMIProcessData?.generateIsoByteRequest()
                HitServer.hitServer(byteArrayRequest!!, { result, success ->
                    if (success && !TextUtils.isEmpty(result)) {
                        val responseIsoData: IsoDataReader = readIso(result, false)
                        logger("Transaction RESPONSE ", "---", "e")
                        logger("Transaction RESPONSE --->>", responseIsoData.isoMap, "e")
                        Log.e(
                            "Success 39-->  ", responseIsoData.isoMap[39]?.parseRaw2String()
                                .toString() + "---->" + responseIsoData.isoMap[58]?.parseRaw2String()
                                .toString()
                        )

                        val responseCode = responseIsoData.isoMap[39]?.parseRaw2String().toString()
                        val hostMsg = responseIsoData.isoMap[58]?.parseRaw2String().toString()
                        val brandEmiAccessCodeData =
                            responseIsoData.isoMap[57]?.parseRaw2String().toString()

                        when (responseCode) {
                            "00" -> {
                                ROCProviderV2.incrementFromResponse(
                                    ROCProviderV2.getRoc(AppPreference.getBankCode()).toString(),
                                    AppPreference.getBankCode()
                                )
                                GlobalScope.launch(Dispatchers.Main) {
                                    //Processing BrandEMIMasterSubCategoryData:-
                                    stubbingBrandEMIAccessCodeDataToList(
                                        brandEmiAccessCodeData,
                                        hostMsg
                                    )
                                }
                            }
                            "-1" -> {
                                GlobalScope.launch(Dispatchers.Main) {
                                    iDialog?.hideProgress()
                                    iDialog?.alertBoxWithAction(null, null,
                                        getString(R.string.info), "No Record Found",
                                        false, getString(R.string.positive_button_ok),
                                        {
                                            parentFragmentManager.popBackStack()
                                        }, {})
                                }
                            }
                            else -> {
                                ROCProviderV2.incrementFromResponse(
                                    ROCProviderV2.getRoc(AppPreference.getBankCode()).toString(),
                                    AppPreference.getBankCode()
                                )
                            }
                        }
                    } else {
                        ROCProviderV2.incrementFromResponse(
                            ROCProviderV2.getRoc(AppPreference.getBankCode()).toString(),
                            AppPreference.getBankCode()
                        )
                        GlobalScope.launch(Dispatchers.Main) {
                            iDialog?.hideProgress()
                            iDialog?.alertBoxWithAction(null, null,
                                getString(R.string.error), result,
                                false, getString(R.string.positive_button_ok),
                                { parentFragmentManager.popBackStackImmediate() }, {})
                        }
                    }
                }, {})
            }
        }
        //endregion
    }
    //endregion

    //region=================================Stubbing BrandEMI Product Data and Display in List:-
    private fun stubbingBrandEMIAccessCodeDataToList(brandEMIAccessData: String, hostMsg: String) {
        GlobalScope.launch(Dispatchers.Main) {
            if (!TextUtils.isEmpty(brandEMIAccessData)) {
                val dataList = parseDataListWithSplitter("|", brandEMIAccessData)
                if (dataList.isNotEmpty()) {
                    // and iterate further on record data only:-
                    var tempDataList = mutableListOf<String>()
                    tempDataList = dataList.subList(2, dataList.size)
                    for (i in tempDataList.indices) {
                        if (!TextUtils.isEmpty(tempDataList[i])) {
                            val splitData = parseDataListWithSplitter(
                                SplitterTypes.CARET.splitter, tempDataList[i]
                            )

                            brandEmiAccessCodeList.add(
                                BrandEMIAccessDataModal(
                                    splitData[0], splitData[1],
                                    splitData[2], splitData[3],
                                    splitData[4], splitData[5],
                                    splitData[6], splitData[7],
                                    splitData[8], splitData[9],
                                    splitData[10], splitData[11],
                                    splitData[12], splitData[13],
                                    splitData[14], splitData[15],
                                    splitData[16], splitData[17],
                                    splitData[18], splitData[19],
                                    splitData[20], splitData[21],
                                    splitData[22], splitData[23],
                                    splitData[24], splitData[25],
                                    splitData[26], splitData[27],
                                    splitData[28], splitData[29],
                                    splitData[30], splitData[31],
                                )
                            )
                        }
                    }

                    /* if (brandEmiProductDataList.isNotEmpty()) {
                         brandEMIMasterSubCategoryAdapter.refreshAdapterList(brandEmiProductDataList)
                     }*/

                    /* //Refresh Field57 request value for Pagination if More Record Flag is True:-
                     if (moreDataFlag == "1") {
                         field57RequestData =
                             "${EMIRequestType.BRAND_EMI_Product.requestType}^$totalRecord^${brandEMIDataModal?.getBrandID()}^${brandEMIDataModal?.getCategoryID()}"
                         fetchBrandEMIProductDataFromHost()
                         Log.d("FullDataList:- ", brandEmiProductDataList.toString())
                     } else {
                         iDialog?.hideProgress()
                         binding?.brandEmiProductFloatingButton?.visibility = View.VISIBLE
                         Log.d("Full Product Data:- ", Gson().toJson(brandEmiProductDataList))
                     }*/
                }
            } else {
                GlobalScope.launch(Dispatchers.Main) {
                    iDialog?.hideProgress()
                }
            }
        }
    }
    //endregion
}

//region=============================Brand EMI Master Category Data Modal==========================
@Parcelize
data class BrandEMIAccessDataModal(
    var emiCode: String,
    var bankID: String,
    var bankTID: String,
    var issuerID: String,
    var tenure: String,
    var brandID: String,
    var productID: String,
    var emiSchemeID: String,
    var transactionAmount: String,
    var discountAmount: String,
    var loanAmount: String,
    var interestAmount: String,
    var emiAmount: String,
    var cashBackAmount: String,
    var netPayAmount: String,
    var processingFee: String,
    var processingFeeRate: String,
    var totalProcessingFee: String,
    var brandName: String,
    var issuerName: String,
    var productName: String,
    var productCode: String,
    var productModal: String,
    var productCategoryName: String,
    var productSerialCode: String,
    var skuCode: String,
    var totalInterest: String,
    var schemeTAndC: String,
    var schemeTenureTAndC: String,
    var schemeDBDTAndC: String,
    var discountCalulatedValue: String,
    var cashBackCalculatedValue: String,
) : Parcelable
//endregion