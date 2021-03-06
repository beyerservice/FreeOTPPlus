package org.fedorahosted.freeotp

import java.lang.reflect.Type
import java.util.ArrayList
import java.util.LinkedList

import com.google.gson.reflect.TypeToken
import org.fedorahosted.freeotp.Token.TokenUriInvalidException

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class TokenPersistence(ctx: Context) {
    private val prefs: SharedPreferences
    private val gson: Gson

    private val tokenOrder: MutableList<String>
        get() {
            val type = object : TypeToken<List<String>>() {

            }.type
            val str = prefs.getString(ORDER, "[]")
            val order = gson.fromJson<List<String>>(str, type)
            return order as MutableList<String>? ?: LinkedList<String>()
        }

    private fun setTokenOrder(order: List<String>): SharedPreferences.Editor {
        return prefs.edit().putString(ORDER, gson.toJson(order))
    }

    init {
        prefs = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun length(): Int {
        return tokenOrder.size
    }

    operator fun get(position: Int): Token? {
        val key = tokenOrder[position]
        val str = prefs.getString(key, null)

        try {
            return gson.fromJson(str, Token::class.java)
        } catch (jse: JsonSyntaxException) {
            // Backwards compatibility for URL-based persistence.
            try {
                return Token(str, true)
            } catch (tuie: TokenUriInvalidException) {
                tuie.printStackTrace()
            }

        }

        return null
    }

    @Throws(TokenUriInvalidException::class)
    fun add(token: Token) {
        val key = token.id

        if (prefs.contains(key))
            return

        val order = tokenOrder
        order.add(0, key)
        setTokenOrder(order).putString(key, gson.toJson(token)).apply()
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition)
            return

        val order = tokenOrder
        if (fromPosition < 0 || fromPosition > order.size)
            return
        if (toPosition < 0 || toPosition > order.size)
            return

        order.add(toPosition, order.removeAt(fromPosition))
        setTokenOrder(order).apply()
    }

    fun delete(position: Int) {
        val order = tokenOrder
        val key = order.removeAt(position)
        setTokenOrder(order).remove(key).apply()
    }

    fun save(token: Token) {
        prefs.edit().putString(token.id, gson.toJson(token)).apply()
    }

    fun toJSON(): String {
        val tokenList = ArrayList<Token>(length())
        for (i in 0..length() - 1) {
            tokenList.add(get(i)!!)
        }

        val tokenOrder = tokenOrder

        return gson.toJson(SavedTokens(tokenList, tokenOrder))
    }

    fun importFromJSON(jsonString: String) {
        val savedTokens = gson.fromJson(jsonString, SavedTokens::class.java)

        setTokenOrder(savedTokens.tokenOrder).apply()

        for (token in savedTokens.tokens) {
            save(token)
        }
        prefs.edit().apply()
    }

    companion object {
        private val NAME = "tokens"
        private val ORDER = "tokenOrder"

        fun addWithToast(ctx: Context, uri: String): Token? {
            try {
                val token = Token(uri)
                TokenPersistence(ctx).add(token)
                return token
            } catch (e: TokenUriInvalidException) {
                Toast.makeText(ctx, R.string.invalid_token, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }

            return null
        }
    }
}
