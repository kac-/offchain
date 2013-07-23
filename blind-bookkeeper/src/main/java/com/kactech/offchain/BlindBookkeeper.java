package com.kactech.offchain;

import java.math.BigInteger;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

public class BlindBookkeeper {
	Wallet wallet;
	BigInteger spamThreshold;
	BigInteger accountCreationFee;
	BigInteger transactionFee;

	public void onNetworkTransactionReceived(Transaction tx) {
		BigInteger amount = tx.getValueSentToMe(wallet);
		if (amount.compareTo(spamThreshold) < 0)
			return;
		Address address;
		try {
			address = tx.getInput(0).getFromAddress();
		} catch (ScriptException e) {
			e.printStackTrace();
			addGratis(tx);
			syncWife();
			return;
		}
		if (!accountExist(address))
			if (amount.compareTo(accountCreationFee) < 0) {
				addGratis(tx);
				syncWife();
				return;
			} else
				createAccount(address);
		addFunds(address, amount);
		syncWife();
	}

	boolean accountExist(Address address) {
		return false;
	}

	void createAccount(Address address) {

	}

	// account deposit 
	void addFunds(Address address, BigInteger amount) {

	}

	// we've got some money for free, admin wi'll take those
	void addGratis(Transaction tx) {

	}

	void syncWife() {
		if (!sendTxToWife())
			panic();
	}

	boolean sendTxToWife() {
		return false;
	}

	void panic() {
		broadcastTx();
		// mangle memory
		// die
		System.exit(-1);
	}

	// broast final tx to the network
	void broadcastTx() {

	}

}
