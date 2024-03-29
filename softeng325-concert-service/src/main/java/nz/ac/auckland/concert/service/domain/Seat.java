package nz.ac.auckland.concert.service.domain;

import nz.ac.auckland.concert.common.dto.SeatDTO;
import nz.ac.auckland.concert.common.types.PriceBand;
import nz.ac.auckland.concert.common.types.SeatNumber;
import nz.ac.auckland.concert.common.types.SeatRow;
import nz.ac.auckland.concert.common.types.SeatStatus;
import nz.ac.auckland.concert.service.domain.jpa.LocalDateTimeConverter;
import nz.ac.auckland.concert.service.domain.jpa.SeatNumberConverter;
import nz.ac.auckland.concert.utility.SeatUtility;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "SEATS")
public class Seat implements Serializable {

    @Version
    private int version;

    @Id
    @ManyToOne
    private Concert concert;

    @Id
    @Column(name = "DATE_TIME", nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime dateTime;

    @Id
    @Column(name = "SEAT_ROW", nullable = false)
    private SeatRow seatRow;

    @Id
    @Column(name = "SEAT_NUMBER", nullable = false)
    @Convert(converter = SeatNumberConverter.class)
    private SeatNumber seatNumber;

    @Column(name = "STATUS", nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus seatStatus = SeatStatus.FREE;

    @Column(name = "PRICE_BAND", nullable = false)
    @Enumerated(EnumType.STRING)
    private PriceBand priceBand;

    @Column(name = "TIME_STAMP")
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime timeStamp;

    @ManyToOne
    @JoinColumn(name="RID")
    private Reservation _reservation;

    public Seat() {}

    public Seat(Concert concert, LocalDateTime dateTime, SeatRow seatRow, SeatNumber seatNumber) {
        this.concert = concert;
        this.dateTime = dateTime;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;

        this.priceBand = SeatUtility.determinePriceBand(this.seatRow);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Concert getConcert() {
        return concert;
    }

    public void setConcert(Concert concert) {
        this.concert = concert;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public SeatRow getSeatRow() {
        return seatRow;
    }

    public void setSeatRow(SeatRow seatRow) {
        this.seatRow = seatRow;
    }

    public SeatNumber getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(SeatNumber seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatStatus getSeatStatus() {
        return seatStatus;
    }

    public void setSeatStatus(SeatStatus seatStatus) {
        this.seatStatus = seatStatus;
    }

    public PriceBand getPriceBand() {
        return priceBand;
    }

    public void setPriceBand(PriceBand priceBand) {
        this.priceBand = priceBand;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public Reservation get_reservation() {
        return _reservation;
    }

    public void set_reservation(Reservation _reservation) {
        this._reservation = _reservation;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SeatDTO))
            return false;
        if (obj == this)
            return true;

        Seat rhs = (Seat) obj;
        return new EqualsBuilder().
                append(this.seatRow, rhs.seatRow).
                append(this.seatNumber, rhs.seatNumber).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(this.seatRow).
                append(this.seatNumber).
                hashCode();
    }

    @Override
    public String toString() {
        return this.seatRow + this.seatNumber.toString();
    }
}
