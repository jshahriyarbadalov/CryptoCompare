package com.shahriyar.cryptoconvert.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.shahriyar.cryptoconvert.database.AppDataBase
import com.shahriyar.cryptoconvert.model.CoinPriceInfo
import com.shahriyar.cryptoconvert.model.CoinPriceInfoRawData
import com.shahriyar.cryptoconvert.network.ApiFactory
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class CoinViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDataBase.getInstance(application)
    private val compositeDisposable = CompositeDisposable()

    val priceList = db.coinPriceInfoDao().getPriceList()

    fun getDetailInfo(fSym:String):LiveData<CoinPriceInfo>{
        return db.coinPriceInfoDao().getPriceInfoAboutCoin(fSym)
    }
    init {
        loadData()
    }

    private fun loadData() {
        val disposable = ApiFactory.apiService.getTopCoinsInfo(limit = 50)
            .map { it.data?.map { it.coinInfo?.name }?.joinToString(",") }
            .flatMap { ApiFactory.apiService.getFullPriceList(fSyms = it.toString()) }
            .map { getPriceListFromRawData(it) }
            .delaySubscription(10,TimeUnit.SECONDS)
            .repeat()
            .retry()
            .subscribeOn(Schedulers.io())
            .subscribe({
                db.coinPriceInfoDao().insertPriceList(it)
                Log.d("TEST_OF_LOADING_DATA", "Success $it")
            }, {
                Log.d("ERROR_OF_LOADING_DATA", it.message)
            })
        compositeDisposable.add(disposable)
    }

    private fun getPriceListFromRawData(coinPriceInfoRawData: CoinPriceInfoRawData)
    :List<CoinPriceInfo>{
        val result=ArrayList<CoinPriceInfo>()
        val jsonObject= coinPriceInfoRawData.coinPriceInfoJsonObject ?: return result
        val coinKeySet=jsonObject.keySet()
        for(coinKey in coinKeySet){
            val currencyJson=jsonObject.getAsJsonObject(coinKey)
            val currencyKeySet=currencyJson.keySet()
            for(currencyKey in currencyKeySet){
                val priceInfo=Gson().fromJson(currencyJson.getAsJsonObject(currencyKey),
                    CoinPriceInfo::class.java)
                result.add(priceInfo)
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}