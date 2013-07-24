package com.kactech.offchain;

import java.math.BigInteger;
import java.util.List;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;

public class BlindBookkeeper {
	NetworkParameters params;

	Wallet wallet;
	BigInteger spamThreshold;
	BigInteger accountCreationFee;
	BigInteger transactionFee;
	BigInteger networkMinimalOutput;

	Transaction book;
	Transaction panicBook;

	Address adminAddress;
	Address panicAddress;

	void initBooks() {
		book = new Transaction(params);
		panicBook = new Transaction(params);

		book.addOutput(new TransactionOutput(params, book, BigInteger.ZERO, adminAddress));
		book.addOutput(new TransactionOutput(params, book, BigInteger.ZERO, panicAddress));
		panicBook.addOutput(new TransactionOutput(params, panicBook, BigInteger.ZERO, adminAddress));
		panicBook.addOutput(new TransactionOutput(params, panicBook, BigInteger.ZERO, panicAddress));
	}

	int getOutputIndex(Address address) {
		byte[] hash = address.getHash160();
		List<TransactionOutput> outputs = book.getOutputs();
		for (int i = 0; i < outputs.size(); i++)
			try {
				if (outputs.get(i).getScriptPubKey().getPubKeyHash().equals(hash))
					return i;
			} catch (ScriptException e) {
				// TODO can be that out output generates script exception? I think no
				throw new RuntimeException(e);
			}
		return -1;
	}

	public void onNetworkTransactionReceived(Transaction tx) {
		BigInteger amount = tx.getValueSentToMe(wallet);
		if (amount.compareTo(spamThreshold) < 0)
			return;
		// get first address that sends to me something, this will be sender's account address
		Address address = null;
		try {
			for (TransactionOutput to : tx.getOutputs())
				if (to.isMine(wallet))
					address = to.getScriptPubKey().getToAddress(params);
		} catch (ScriptException e) {
			e.printStackTrace();
			addGratis(tx);
			syncWife();
			return;
		}
		if (!accountExist(address))
			if (amount.compareTo(accountCreationFee.add(networkMinimalOutput)) < 0) {
				addGratis(tx);
				syncWife();
				return;
			} else
				createAccount(address, accountCreationFee.negate());
		addFunds(address, tx);
		syncWife();
	}

	boolean accountExist(Address address) {
		return getOutputIndex(address) >= 0;
	}

	int createAccount(Address address, BigInteger initialBalance) {
		int index = book.getOutputs().size();
		book.addOutput(new TransactionOutput(params, book, initialBalance, address));
		panicBook.addOutput(new TransactionOutput(params, book, initialBalance, address));

		// TODO maybe better place for it is addFunds?
		book.getOutput(0).setValue(book.getOutput(0).getValue().add(accountCreationFee));
		panicBook.getOutput(0).setValue(panicBook.getOutput(0).getValue().add(accountCreationFee));

		// TODO adjust tx fee

		return index;
	}

	// account deposit 
	void addFunds(Address address, Transaction tx) {
		for (TransactionOutput to : tx.getOutputs())
			if (to.isMine(wallet)) {
				book.addInput(to);
				panicBook.addInput(to);
			}
		// TODO adjust tx fee

		BigInteger amount = tx.getValueSentToMe(wallet);
		int index = getOutputIndex(address);
		book.getOutput(index).setValue(book.getOutput(index).getValue().add(amount));
		if (index == 0)// in panicBook funds deposited to adminAddress lends into panicAddress - clear, isn't it?
			index = 1;
		panicBook.getOutput(index).setValue(panicBook.getOutput(index).getValue().add(amount));
	}

	// we've got some money for free, admin/panic will take those
	void addGratis(Transaction tx) {
		for (TransactionOutput to : tx.getOutputs())
			if (to.isMine(wallet)) {
				book.addInput(to);
				panicBook.addInput(to);
			}
		// TODO adjust tx fee

		BigInteger amount = tx.getValueSentToMe(wallet);
		book.getOutput(0).setValue(book.getOutput(0).getValue().add(amount));
		panicBook.getOutput(1).setValue(panicBook.getOutput(1).getValue().add(amount));
	}

	// transfer funds from account to account, if 'to account' doesn't exist - create it
	public boolean transfer(Address from, Address to, BigInteger amount) {
		if (amount.compareTo(BigInteger.ZERO) < 0)
			return false;// hehe, nice try
		if (from.getHash160().equals(to.getHash160()))// sending to himself?
			return false;
		int fromIdx = getOutputIndex(from);
		if (fromIdx < 2)// transfers from admin/panic are forbidden
			return false;
		BigInteger fromBalance = book.getOutput(fromIdx).getValue();
		fromBalance = fromBalance.subtract(amount);
		if (fromBalance.compareTo(networkMinimalOutput) < 0)
			return false;//insufficient funds

		int toIdx = getOutputIndex(to);
		if (toIdx < 0) {// 'to account' doesn't exist
			fromBalance = fromBalance.subtract(accountCreationFee);
			if (fromBalance.compareTo(networkMinimalOutput) < 0)
				return false;// 'from account' has insufficient funds to create new account
			if (amount.compareTo(networkMinimalOutput) < 0)//don't let it create unspendable output
				return false;
			toIdx = createAccount(to, BigInteger.ZERO);
		}

		// set balances
		book.getOutput(fromIdx).setValue(fromBalance);
		panicBook.getOutput(fromIdx).setValue(fromBalance);

		book.getOutput(toIdx).setValue(book.getOutput(toIdx).getValue().add(amount));
		panicBook.getOutput(toIdx).setValue(panicBook.getOutput(toIdx).getValue().add(amount));

		return true;
	}

	void syncWife() {
		if (!sendTxToWife())
			panic();
	}

	boolean sendTxToWife() {
		// TODO
		return false;
	}

	void panic() {
		broadcastTx();
		// TODO mangle memory
		// die
		System.exit(-1);
	}

	// broast final tx to the network
	void broadcastTx() {
		// TODO
	}

}
