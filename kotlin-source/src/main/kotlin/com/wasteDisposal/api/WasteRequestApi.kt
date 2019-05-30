package com.wasteDisposal.api

import com.wasteDisposal.POJO.ResponsePojo
import com.wasteDisposal.POJO.WasteRequestPojo
import com.wasteDisposal.flow.WasteRequestFlow
import com.wasteDisposal.schema.WasteRequestSchemaV1
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

@Path("wasteRequestAPI")
class WasteRequestApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ProposalApi>()
    }


    /**
     *
     * WasteRequest API List.
     *
     */

    /**
     *  GetAllWasteRequestByParam
     */

    @GET
    @Path("get/getAllWasteRequest")
    @Produces(MediaType.APPLICATION_JSON)
    fun getWasteRequestByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("wasteType") wasteType: String,
                                @DefaultValue("") @QueryParam("cliente") cliente: String,
                                @DefaultValue("") @QueryParam("fornitore") fornitore: String,
                                @DefaultValue("") @QueryParam("idWasteRequest") idWasteRequest: String,
                                @DefaultValue("") @QueryParam("wasteGps") wasteGps: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("pending") @QueryParam("status") status: String,
                                @DefaultValue("unconsumed") @QueryParam("statusWasteReqBC") statusWasteReqBC: String): Response {

        try{
            var myPage = page

            if(myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(statusWasteReqBC){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)
            val results = builder {

                if(idWasteRequest.length > 0){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(idWasteRequest)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(cliente.length >0){
                    val clienteEqual = WasteRequestSchemaV1.PersistentWasteRequest::clienteName.equal(cliente)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(clienteEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(fornitore.length >0){
                    val fornitoreEqual = WasteRequestSchemaV1.PersistentWasteRequest::fornitoreName.equal(fornitore)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(fornitoreEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(wasteType.length >0){
                    val wasteTypeEqual = WasteRequestSchemaV1.PersistentWasteRequest::wasteType.equal(wasteType)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(wasteTypeEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(wasteGps.length >0){
                    val wasteGpsEqual = WasteRequestSchemaV1.PersistentWasteRequest::wasteGps.equal(wasteGps)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(wasteGpsEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.length > 0 && to.length > 0){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = WasteRequestSchemaV1.PersistentWasteRequest::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(status.length > 0){
                    val statusEqual = WasteRequestSchemaV1.PersistentWasteRequest::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = rpcOps.vaultQueryBy<WasteRequestState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }

        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }


    /**
     *  Insert WasteRequest
     */
    @POST
    @Path("post/insertWasteRequest")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createWasteRequest(req : WasteRequestPojo): Response {

        try {
            val cliente : Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(req.cliente))!!
            val fornitore : Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(req.fornitore))!!
            val syndial : Party = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Syndial,L=Milan,C=IT"))!!

            val signedTx = rpcOps.startTrackedFlow(
                    WasteRequestFlow::Starter,
                    cliente,
                    fornitore,
                    syndial,
                    req
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction "+signedTx.toString()+" committed to ledger.")
            return Response.status(CREATED).entity(resp).build()

        }catch (ex: Throwable){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

}