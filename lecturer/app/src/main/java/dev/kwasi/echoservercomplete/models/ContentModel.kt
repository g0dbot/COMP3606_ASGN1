/*IDS 816034693 816017853*/

package dev.kwasi.echoservercomplete.models

/// The [ContentModel] class represents data that is transferred between devices when multiple
/// devices communicate with each other.
data class ContentModel(val message:String, var senderIp:String)