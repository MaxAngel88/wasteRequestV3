package com.wasteDisposal.api

import com.wasteDisposal.POJO.EarlyProposalPojo
import com.wasteDisposal.POJO.IssueProposalPojo
import com.wasteDisposal.POJO.ResponsePojo
import com.wasteDisposal.flow.EarlyProposalFlow
import com.wasteDisposal.schema.EarlyProposalSchemaV1
import com.wasteDisposal.schema.ProposalSchemaV1
import com.wasteDisposal.state.EarlyProposalState
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
import java.lang.Exception
import java.text.SimpleDateFormat
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.Response.Status.BAD_REQUEST

@Path("earlyProposalAPI")
class EarlyProposalApi (private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ProposalApi>()
    }

    /**
     *
     * EarlyProposal API List.
     *
     */


    /**
     *  GetAllEarlyProposalByParam
     */
    @GET
    @Path("get/getAllEarlyProposal")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllEarlyProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                     @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                     @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                     @DefaultValue("unconsumed") @QueryParam("statusPropBC") statusPropBC: String) : Response {
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

                if (from.length > 0 && to.length > 0) {
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    val myFrom = format.parse(from)
                    val myTo = format.parse(to)
                    var dateBetween = EarlyProposalSchemaV1.EarlyPersistentProposal::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = rpcOps.vaultQueryBy<EarlyProposalState>(
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
     *  InsertEarlyProposal
     */
    @POST
    @Path("post/insertEarlyProposal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createEarlyProposal(req: EarlyProposalPojo): Response {

        try {
            val syndial: Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Syndial,L=Milan,C=IT"))!!

            val earlyProposal = rpcOps.startTrackedFlow(
                    EarlyProposalFlow::Starter,
                    syndial,
                    req
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "New Proposal committed to ledger.", earlyProposal)

            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *  IssueEarlyProposal
     */
    @POST
    @Path("post/issueEarlyProposal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun issueEarlyProposal(req: IssueProposalPojo): Response {

        try {
            val fornitore: Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(req.fornitore))!!

            val proposal = rpcOps.startTrackedFlow(
                    EarlyProposalFlow::Issuer,
                    fornitore,
                    req
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "New WasteRequest committed to ledger.", proposal)
            return Response.status(CREATED).entity(resp).build()

        } catch (ex: Exception) {
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!, "")
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }
}