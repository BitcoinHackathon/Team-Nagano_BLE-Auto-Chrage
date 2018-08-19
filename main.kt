import org.bitcoinj.core.*
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.wallet.Wallet
import java.io.File

fun main(args: Array<String>) {

// 利用するビットコインのネットワーク
// MainNetを利用するにはMainNetParams()を用いる
    val params = TestNet3Params()

// Bitcoinの各種データ(秘密鍵含む)を保存するディレクトリ
// Androidの場合は context.filesDir などを利用するとよい
    val dir = File(".")

// 各データのファイル名のPrefix
    val filePrefix = "testnet"

// WalletAppKitはBitcoinを用いたアプリを作るときに便利なUtilクラス
    val kit = WalletAppKit(params, dir, "").apply {
        // Kitのセットアップを開始する
        startAsync()

        // Kitのセットアップが終わるまで待つ
        awaitRunning()
    }

    val wallet = kit.wallet()

// 残高
// BalanceType.ESTIMATED は承認前のトランザクションも残高計算に含めるというオプション
    val balance = wallet.getBalance(Wallet.BalanceType.ESTIMATED)

// 現在の受取用アドレス
    val address = CashAddressFactory.create().getFromBase58(params, wallet.currentReceiveAddress().toBase58())

// このWalletに関するトランザクションのリストを時間順で取得
    val transactions = wallet.transactionsByTime

// コインを受け取ったときに何か処理する場合に用いる
    wallet.addCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
        // something...
    }

    println(address)
    println(balance)

    //所持している未承認のコインも利用可能にする
    kit.wallet().allowSpendingUnconfirmedTransactions()



//====================================================
    // インポートされた鍵がなければ新しくMultisig用の鍵を作る
// この鍵は管理しやすいようにHD Walletを使って取得/管理しても良い
// 鍵を作ったらWalletにインポートしておく
     val myKey = if (wallet.importedKeys.isEmpty()) ECKey().also { wallet.importKey(it) } else wallet.importedKeys[0]

// このアドレスで用いるもう一つの公開鍵
// 今回は便宜上、同じウォレット内で行う
// 通常は、他のウォレットで作成した公開鍵のバイト配列(ECKey#getPubKey())を用いて取得してくる
    val partnerKey = if (wallet.importedKeys.size < 2) ECKey().also { wallet.importKey(it) } else wallet.importedKeys[1]
    val keys = arrayListOf(myKey, partnerKey)

// 今回は2 of 2のMultisigアドレスを作成したいため、引数に2を渡す
// redeemScript.program のバイト列は、このアドレスに送られたコインを使うときに必要になるため
// 保持しておく必要がある
    val redeemScript = ScriptBuilder.createRedeemScript(2, keys)
    val script = ScriptBuilder.createP2SHOutputScript(redeemScript)
    val multisigAddress = Address.fromP2SHScript(params, script)

    println(multisigAddress)

    // 作成したP2SHアドレスを監視リスト(Bloom Filter)に加える
// こうすることでこのアドレス宛のトランザクションを検知できるようになる
    wallet.addWatchedAddress(multisigAddress)


    val to: Address = CashAddressFactory.create().getFromFormattedAddress(params,"bchtest:qp8xrhmaarltz4yn3c77w2klpmyqj8qpsggp3nur96")

// 利用したいMultisigアドレス宛のUTXO
    val utxo = wallet.getTransaction(Sha256Hash.wrap("e6cae0e84b08e3c8a66b05a074641c45d7ffddb0079d411fffbb997d7bea51b5"))!!
    val multisigOutput = utxo.getWalletOutputs(wallet).first()

// 手数料として少し送金額を減らす
// 本来ならばトランザクションのバイト数に応じて決めるのがよい
// ex.) 1 satoshi per byte
    val sendAmount = multisigOutput.value.subtract(Coin.valueOf(1000))
    val spendTx = Transaction(params).apply {
        addOutput(sendAmount, to)
        addInput(multisigOutput)
    }

// トランザクションのハッシュを作成する
    val sigHash = spendTx.hashForSignatureWitness(0, redeemScript, multisigOutput.value, Transaction.SigHash.ALL, false)

// トランザクションのハッシュ値に署名する
    val signature = myKey.sign(sigHash)

// もう一つの鍵で署名する
// 署名をエンコード/デコードするには
// ECKey.ECDSASignature#encodeToDER, decodeFromDER を利用すると良い
    val partnerSignature = partnerKey.sign(sigHash)
    val sigs = arrayOf(partnerSignature, signature).map { TransactionSignature(it, Transaction.SigHash.ALL, false, true) }.toList()

// 署名をinputに設定する
    spendTx.inputs.first().scriptSig = ScriptBuilder.createP2SHMultiSigInputScript(sigs, redeemScript)

// 検証
// うまく署名等ができていないと例外が発生する
    spendTx.inputs.first().verify()
//
//// ブロードキャスト
    wallet.commitTx(spendTx)


    println("address"+address)
    println("balance"+balance)
    println("MultiSigAddress: "+"e6cae0e84b08e3c8a66b05a074641c45d7ffddb0079d411fffbb997d7bea51b5")
    println("student_signature: "+signature)
    println("teature_signature: "+partnerSignature)
    Thread.sleep(Long.MAX_VALUE)


}