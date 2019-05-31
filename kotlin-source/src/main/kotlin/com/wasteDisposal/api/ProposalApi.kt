package com.wasteDisposal.api

import com.wasteDisposal.POJO.IssueWasteRequestPojo
import com.wasteDisposal.POJO.ProposalPojo
import com.wasteDisposal.POJO.ResponsePojo
import com.wasteDisposal.POJO.SetProposalPojo
import com.wasteDisposal.flow.ProposalFlow
import com.wasteDisposal.flow.WasteRequestFlow
import com.wasteDisposal.schema.ProposalSchemaV1
import com.wasteDisposal.state.ProposalState
import com.wasteDisposal.state.WasteRequestState
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import net.corda.core.node.services.vault.QueryCriteria
import java.text.SimpleDateFormat
import java.util.*

@Path("proposalAPI")
class ProposalApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ProposalApi>()
    }

    /**
     *
     * Proposal API List.
     *
     */

    /**
     *  GetAllProposalByParam
     */
    @GET
    @Path("get/getAllProposal")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("idProposal") idProposal: String,
                                @DefaultValue("") @QueryParam("codFornitore") codFornitore: String,
                                @DefaultValue("") @QueryParam("wasteType") wasteType: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("pending") @QueryParam("status") status: String,
                                @DefaultValue("unconsumed") @QueryParam("statusPropBC") statusPropBC: String): Response {

        try {
            var myPage = page

            if (myPage < 1) {
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when (statusPropBC) {
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val results = builder {

                var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)


                if (idProposal.length > 0) {
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (codFornitore.length > 0) {
                    val idEqual = ProposalSchemaV1.PersistentProposal::codFornitore.equal(codFornitore)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (wasteType.length > 0) {
                    val idEqual = ProposalSchemaV1.PersistentProposal::wasteType.equal(wasteType)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (from.length > 0 && to.length > 0) {
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    val myFrom = format.parse(from)
                    val myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (status.length > 0) {
                    val statusEqual = ProposalSchemaV1.PersistentProposal::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = rpcOps.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *  GetAllMyProposal
     */
    @GET
    @Path("get/getAllReceivedProposal")
    @Produces(MediaType.APPLICATION_JSON)
    fun getReceivedProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                     @DefaultValue("") @QueryParam("idProposal") idProposal: String,
                                     @DefaultValue("") @QueryParam("codCliente") codCliente: String,
                                     @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                     @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                     @DefaultValue("pending") @QueryParam("status") status: String,
                                     @DefaultValue("unconsumed") @QueryParam("statusPropBC") statusPropBC: String): Response {

        try {
            var myPage = page

            if (myPage < 1) {
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when (statusPropBC) {
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val result = builder {
                var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                val fornitoreEqual = ProposalSchemaV1.PersistentProposal::fornitore.equal(myLegalName.toString())
                val firstCriteria = QueryCriteria.VaultCustomQueryCriteria(fornitoreEqual, myStatus)
                criteria = criteria.and(firstCriteria)

                if (idProposal.length > 0) {
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (codCliente.length > 0) {
                    val idEqual = ProposalSchemaV1.PersistentProposal::codCliente.equal(codCliente)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (from.length > 0 && to.length > 0) {
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if (status.length > 0) {
                    val statusEqual = ProposalSchemaV1.PersistentProposal::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = rpcOps.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *  InsertProposal
     */
    @POST
    @Path("post/insertProposal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createProposal(req: ProposalPojo): Response {

        try {
            val fornitore: Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(req.fornitore))!!
            val syndial: Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Syndial,L=Milan,C=IT"))!!

            val signedTx = rpcOps.startTrackedFlow(
                    ProposalFlow::Starter,
                    fornitore,
                    syndial,
                    req
            ).returnValue.getOrThrow()

            var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val result = rpcOps.vaultQueryBy<ProposalState>(
                    criteria,
                    PageSpecification(1, DEFAULT_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states.get(0)


            val resp = ResponsePojo("SUCCESS", "transaction " + signedTx.toString() + " committed to ledger.", result.state.data.linearId.id.toString())
            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *  IssueWasteRequest
     */
    @POST
    @Path("post/issueWasteRequest")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun issueWasteRequest(req: IssueWasteRequestPojo): Response {

        try {
            val signedTx = rpcOps.startTrackedFlow(
                    WasteRequestFlow::Issuer,
                    req.id
            ).returnValue.getOrThrow()

            var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val result = rpcOps.vaultQueryBy<WasteRequestState>(
                    criteria,
                    PageSpecification(1, DEFAULT_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states.get(0)


            val resp = ResponsePojo("SUCCESS", "transaction " + signedTx.toString() + " committed to ledger.", result.state.data.linearId.id.toString())
            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     *  IssueUpdateProposal
     */
    @POST
    @Path("post/issueUpdateProposal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun issueUpdateProposal(req: SetProposalPojo): Response {

        try {
            val signedTx = rpcOps.startTrackedFlow(
                    ProposalFlow::IssuerUpdateProposal,
                    req.id,
                    req.newStatus
            ).returnValue.getOrThrow()

            var criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val result = rpcOps.vaultQueryBy<ProposalState>(
                    criteria,
                    PageSpecification(1, DEFAULT_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states.get(0)


            val resp = ResponsePojo("SUCCESS", "transaction " + signedTx.toString() + " committed to ledger.", result.state.data.linearId.id.toString())
            return Response.status(CREATED).entity(resp).build()


        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }
}
