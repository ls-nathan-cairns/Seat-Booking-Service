package nz.ac.auckland.concert.service.services.resources;

import nz.ac.auckland.concert.common.dto.NewsItemDTO;
import nz.ac.auckland.concert.common.message.Messages;
import nz.ac.auckland.concert.service.domain.NewsItem;
import nz.ac.auckland.concert.service.domain.User;
import nz.ac.auckland.concert.service.mappers.NewsItemMapper;
import nz.ac.auckland.concert.service.services.PersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Path("/news_items")
public class NewsItemResource {
    private Map<Cookie, AsyncResponse> responseList;

    private PersistenceManager persistenceManager;

    private Logger logger =
            LoggerFactory.getLogger(NewsItemResource.class);

    public NewsItemResource() {
        this.responseList = new ConcurrentHashMap<>();
        this.persistenceManager = PersistenceManager.instance();
    }

    @POST
    @Consumes({MediaType.APPLICATION_XML})
    public Response createNewsItem(NewsItemDTO newsItemDTO) {
        EntityManager em = this.persistenceManager.createEntityManager();

        try {
            em.getTransaction().begin();

            if (em.find(NewsItem.class, newsItemDTO.getId()) != null) {
                this.logger.debug("News Item with id already exists!");
                return Response
                        .status(Response.Status.CONFLICT)
                        .entity(Messages.CREATE_NEWS_ITEM_ALREADY_EXISTS)
                        .build();
            }

            NewsItem newsItem = NewsItemMapper.toDomain(newsItemDTO);

            em.persist(newsItem);

            em.getTransaction().commit();
            return Response
                    .created(URI.create("/news_items/" + newsItem.getId()))
                    .build();
        } catch (Exception e) {
            return Response.serverError().entity(Messages.SERVICE_COMMUNICATION_ERROR).build();
        } finally {
            em.close();
            this.process(newsItemDTO);
        }
    }

    @GET
    @Path("/sub")
    @Consumes(MediaType.APPLICATION_XML)
    public Response subscribe(@Suspended AsyncResponse response, @CookieParam("AuthToken") Cookie authToken) {
        EntityManager em = this.persistenceManager.createEntityManager();
        this.logger.debug("Creating sub at server");

        try {
            em.getTransaction().begin();

            // Check there is an auth token
            if (authToken == null) {
                this.logger.debug("No auth token");
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(Messages.UNAUTHENTICATED_REQUEST)
                        .build();
            }

            // Check there is a user with auth token
            User user = em.createQuery("SELECT u from User u WHERE u.authToken = :token", User.class)
                    .setParameter("token", authToken.getValue()).getSingleResult();

            if (user == null) {
                this.logger.debug("No user corresponding to auth token");
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(Messages.BAD_AUTHENTICATON_TOKEN)
                        .build();
            }

            this.logger.debug("Subscribing user " + user.getUsername());

            List<NewsItem> newsItems = em.createQuery("SELECT n FROM NewsItem n", NewsItem.class)
                    .getResultList();
            boolean after = false;
            for (NewsItem n : newsItems) {
                if (after) {
                    response.resume(NewsItemMapper.toDTO(n));
                }
                if (user.getLastRead() != null && n.getId() == user.getLastRead().getId()) {
                    after = true;
                }
            }

            this.responseList.put(authToken, response);

            em.getTransaction().commit();
            return Response
                    .ok()
                    .build();
        } catch (Exception e) {
            return Response
                    .serverError()
                    .entity(Messages.SERVICE_COMMUNICATION_ERROR)
                    .build();
        } finally {
            em.close();
        }
    }

    @DELETE
    @Path("/sub")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response unsubscribe(@CookieParam("AuthToken") Cookie authToken) {
        EntityManager em = this.persistenceManager.createEntityManager();

        try {
            em.getTransaction().begin();
            // Check there is an auth token
            if (authToken == null) {
                this.logger.debug("No auth token");
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(Messages.UNAUTHENTICATED_REQUEST)
                        .build();
            }

            // Check there is a user with auth token
            User user = em.createQuery("SELECT u from User u WHERE u.authToken = :token", User.class)
                    .setParameter("token", authToken.getValue()).getSingleResult();

            if (user == null) {
                this.logger.debug("No user corresponding to auth token");
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(Messages.BAD_AUTHENTICATON_TOKEN)
                        .build();
            }

            this.responseList.remove(authToken);
            this.logger.debug("Response list has size of " + this.responseList.size());

            em.merge(user);

            em.getTransaction().commit();

            return Response
                    .ok()
                    .build();
        } catch (Exception e) {
            return Response
                    .serverError()
                    .entity(Messages.SERVICE_COMMUNICATION_ERROR)
                    .build();
        } finally {
            em.close();
        }
    }

    public void process(NewsItemDTO newsItemDTO) {
        EntityManager em = this.persistenceManager.createEntityManager();
        this.logger.debug("Informing subscribers...");

        try {
            em.getTransaction().begin();

            this.logger.debug("Response list has size of " + this.responseList.size());
            for (Cookie authToken : this.responseList.keySet()) {
                this.responseList.get(authToken).resume(newsItemDTO);

                User user = em.createQuery("SELECT u FROM User u WHERE u.authToken = :authToken", User.class)
                        .setParameter("authToken", authToken.getValue())
                        .getSingleResult();
                NewsItem newsItem = em.find(NewsItem.class, newsItemDTO.getId());

                if (user != null) {
                    user.setLastRead(newsItem);
                    em.merge(user);
                }

                this.logger.debug("Informed user " + user.getUsername());
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
