package net.corda.node.services

import net.corda.contracts.asset.Cash
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.composite
import net.corda.core.node.services.TxWritableStorageService
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.*
import kotlin.test.assertEquals

class NodeVaultServiceTest {
    lateinit var dataSource: Closeable
    lateinit var database: Database
    private val dataSourceProps = makeTestDataSourceProperties()

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val dataSourceAndDatabase = configureDatabase(dataSourceProps)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
    }

    @After
    fun tearDown() {
        dataSource.close()
        LogHelper.reset(NodeVaultService::class)
    }

    @Test
    fun `states not local to instance`() {
        databaseTransaction(database) {
            val services1 = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }
            services1.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = services1.vaultService.unconsumedStates<Cash.State>()
            assertThat(w1).hasSize(3)

            val originalStorage = services1.storageService
            val originalVault = services1.vaultService
            val services2 = object : MockServices() {
                override val vaultService: VaultService get() = originalVault

                // We need to be able to find the same transactions as before, too.
                override val storageService: TxWritableStorageService get() = originalStorage

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }

            val w2 = services2.vaultService.unconsumedStates<Cash.State>()
            assertThat(w2).hasSize(3)
        }
    }

    @Test
    fun `states for refs`() {
        databaseTransaction(database) {
            val services1 = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }
            services1.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = services1.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(w1).hasSize(3)

            val stateRefs = listOf(w1[1].ref, w1[2].ref)
            val states = services1.vaultService.statesForRefs(stateRefs)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `states soft locking reserve and release`() {
        databaseTransaction(database) {
            val services1 = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }
            services1.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val unconsumedStates = services1.vaultService.unconsumedStates<Cash.State>()
            assertThat(unconsumedStates).hasSize(3)

            val stateRefsToSoftLock = setOf(unconsumedStates[1].ref, unconsumedStates[2].ref)

            // soft lock two of the three states
            val softLockId = UUID.randomUUID()
            services1.vaultService.softLockReserve(softLockId, stateRefsToSoftLock)

            // all softlocked states
            assertThat(services1.vaultService.softLockedStates<Cash.State>()).hasSize(2)
            // my softlocked states
            assertThat(services1.vaultService.softLockedStates<Cash.State>(softLockId)).hasSize(2)

            // excluding softlocked states
            val unlockedStates1 = services1.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false)
            assertThat(unlockedStates1).hasSize(1)

            // soft lock release one of the states explicitly
            services1.vaultService.softLockRelease(softLockId, setOf(unconsumedStates[1].ref))
            val unlockedStates2 = services1.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false)
            assertThat(unlockedStates2).hasSize(2)

            // soft lock release the rest by id
            services1.vaultService.softLockRelease(softLockId)
            val unlockedStates = services1.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false)
            assertThat(unlockedStates).hasSize(3)

            // should be back to original states
            assertThat(unconsumedStates).isEqualTo(unlockedStates)
        }
    }

    lateinit var servicesSL: MockServices
    lateinit var persister: HibernateObserver

    @Test
    fun `states soft locking query granularity`() {
        databaseTransaction(database) {
            servicesSL = object : MockServices() {
                override val vaultService: NodeVaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }
            persister = HibernateObserver(servicesSL.vaultService, NodeSchemaService())

            servicesSL.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            servicesSL.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 10, 10, Random(0L))
            servicesSL.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 10, 10, Random(0L))
        }

        databaseTransaction(database) {
            val allStates = servicesSL.vaultService.unconsumedStates<Cash.State>()
            assertThat(allStates).hasSize(30)

            for (i in 1..5 ) {
                val spendableStatesUSD = (servicesSL.vaultService as NodeVaultService).unconsumedStatesForSpending<Cash.State>(20.DOLLARS)
                spendableStatesUSD.forEach(::println)
                servicesSL.vaultService.softLockReserve(UUID.randomUUID(), spendableStatesUSD.map { it.ref }.toSet())
            }
            assertThat(servicesSL.vaultService.softLockedStates<Cash.State>()).hasSize(10)
        }
    }

    @Test
    fun addNoteToTransaction() {
        databaseTransaction(database) {
            val services = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }

            val freshKey = services.legalIdentityKey

            // Issue a txn to Send us some Money
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(usefulTX))

            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 1")
            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 2")
            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 3")
            assertEquals(3, services.vaultService.getTransactionNotes(usefulTX.id).count())

            // Issue more Money (GBP)
            val anotherTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 200.POUNDS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(anotherTX))

            services.vaultService.addNoteToTransaction(anotherTX.id, "GPB Sample Note 1")
            assertEquals(1, services.vaultService.getTransactionNotes(anotherTX.id).count())
        }
    }
}
