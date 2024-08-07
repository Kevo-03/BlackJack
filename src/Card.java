public class Card
{
    private Face face;
    private Suit suit;
    public Card(Face cardFace, Suit cardSuit)
    {
        setFace(cardFace);
        setSuite(cardSuit);
    }

    public void setFace(Face cardFace)
    {
        face = cardFace;
    }

    public void setSuite(Suit cardSuit)
    {
        suit = cardSuit;
    }

    public Face geFace()
    {
        return face;
    }

    public Suit getSuit()
    {
        return suit;
    }

    @Override
    public String toString()
    {
        return String.format("%s of %s",face,suit);
    }
}
